package net.corda.crypto.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.KeyRotationMetadataValues
import net.corda.crypto.core.KeyRotationRecordType
import net.corda.crypto.core.KeyRotationStatus
import net.corda.crypto.rest.KeyRotationRestResource
import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.crypto.rest.response.KeyRotationStatusResponse
import net.corda.crypto.rest.response.ManagedKeyRotationResponse
import net.corda.crypto.rest.response.TenantIdWrappingKeysStatus
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.ForbiddenException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.rest.response.ResponseEntity
import net.corda.schema.Schemas.Crypto.REKEY_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.StateManagerConfig
import net.corda.v5.base.annotations.VisibleForTesting
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
@Component(service = [PluggableRestResource::class])
class KeyRotationRestResourceImpl @Activate constructor(
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : KeyRotationRestResource, PluggableRestResource<KeyRotationRestResource>, Lifecycle {


    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val configKeys = setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.STATE_MANAGER_CONFIG,
    )

    private var publishToKafka: Publisher? = null
    private var stateManagerInit: StateManager? = null
    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, UnmanagedKeyStatus::class.java)

    override val targetInterface: Class<KeyRotationRestResource> = KeyRotationRestResource::class.java
    override val protocolVersion: Int = platformInfoProvider.localWorkerPlatformVersion

    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator<KeyRotationRestResource>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
    )
    private var subscriptionRegistrationHandle: RegistrationHandle? = null
    private var isUp = false
    override val isRunning get() = publishToKafka != null && isUp

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

    private val stateManager: StateManager
        get() = checkNotNull(stateManagerInit) {
            "State manager for key rotation is not initialised."
        }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling KeyRotationRestResource event, $event.")

        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                if (!isUp) {
                    isUp = true
                    signalUpStatus()
                }
            }

            is RegistrationStatusChangeEvent -> {
                configurationReadService.registerComponentForUpdates(lifecycleCoordinator, configKeys)
                if (event.status == LifecycleStatus.UP) {
                    lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }

            is ConfigChangedEvent -> {
                initialiseKafkaPublisher(event.config)
                initialiseStateManager(event.config)
            }

            is StopEvent -> {
                subscriptionRegistrationHandle?.close()
                stateManager.stop()
                publishToKafka?.close()
            }

            else -> {
                logger.error("Unexpected event $event!")
            }
        }
    }

    private fun signalUpStatus() {
        if (isRunning) {
            lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    @VisibleForTesting
    fun initialiseKafkaPublisher(config: Map<String, SmartConfig>) {
        val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)

        // Initialise publisher with messaging config
        publishToKafka?.close()
        publishToKafka =
            publisherFactory.createPublisher(PublisherConfig("KeyRotationRestResource", false), messagingConfig)
                .also { it.start() }
    }

    @VisibleForTesting
    fun initialiseStateManager(config: Map<String, SmartConfig>) {
        val stateManagerConfig = config.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)

        stateManagerInit?.stop()
        stateManagerInit = stateManagerFactory.create(stateManagerConfig, StateManagerConfig.StateType.KEY_ROTATION)
            .also { it.start() }
        logger.debug("State manager created and started {}", stateManager.name)
    }

    override fun getKeyRotationStatus(keyAlias: String): KeyRotationStatusResponse {
        val records = stateManager.findByMetadataMatchingAll(
            listOf(
                MetadataFilter(KeyRotationMetadataValues.ROOT_KEY_ALIAS, Operation.Equals, keyAlias),
                MetadataFilter(
                    KeyRotationMetadataValues.STATUS_TYPE,
                    Operation.Equals,
                    KeyRotationRecordType.KEY_ROTATION
                )
            )
        )

        // if entries are empty, there is no rootKeyAlias data stored in the state manager, so no key rotation is/was in progress
        if (records.isEmpty()) throw ResourceNotFoundException("No key rotation for $keyAlias is in progress.")

        var lastUpdatedTimestamp = Instant.MIN
        var rotationStatus = KeyRotationStatus.DONE
        val result = mutableListOf<Pair<String, TenantIdWrappingKeysStatus>>()
        records.forEach {
            val state = it.value
            val keyRotationStatus = checkNotNull(deserializer.deserialize(state.value))
            result.add(
                state.metadata[KeyRotationMetadataValues.TENANT_ID].toString() to TenantIdWrappingKeysStatus(
                    keyRotationStatus.total,
                    keyRotationStatus.rotatedKeys
                )
            )
            // Get the latest modified time of all the records
            if (state.modifiedTime.isAfter(lastUpdatedTimestamp)) lastUpdatedTimestamp = state.modifiedTime
            if (state.metadata[KeyRotationMetadataValues.STATUS] != KeyRotationStatus.DONE) rotationStatus =
                KeyRotationStatus.IN_PROGRESS
        }

        // newParentKeyAlias and createdTimestamp are in all records, we just need to grab it from one
        val deserializedValueOfOneRecord = checkNotNull(deserializer.deserialize(records.values.first().value))
        return KeyRotationStatusResponse(
            keyAlias,
            deserializedValueOfOneRecord.newParentKeyAlias,
            rotationStatus,
            deserializedValueOfOneRecord.createdTimestamp,
            lastUpdatedTimestamp,
            result
        )
    }

    override fun startKeyRotation(oldKeyAlias: String, newKeyAlias: String): ResponseEntity<KeyRotationResponse> {
        tryWithExceptionHandling(logger, "start key rotation") {
            checkNotNull(publishToKafka)
        }

        validateInputParams(oldKeyAlias, newKeyAlias)

        if (!hasPreviousRotationFinished()) {
            throw ForbiddenException("A key rotation operation is already ongoing, a new one cannot be started until it completes.")
        }

        return doKeyRotation(
            oldKeyAlias,
            newKeyAlias,
            publishRequests = { publishToKafka!!.publish(it) }
        )
    }

    override fun getManagedKeyRotationStatus(tenantId: String): String {
        return "Tested."
    }

    override fun startManagedKeyRotation(tenantId: String): ResponseEntity<ManagedKeyRotationResponse> {
        tryWithExceptionHandling(logger, "start key rotation") {
            checkNotNull(publishToKafka)
        }

        if (tenantId.isEmpty()) throw InvalidInputDataException(
            "Cannot start key rotation. TenantId is not specified."
        )

        return doManagedKeyRotation(
            tenantId,
            publishRequests = { publishToKafka!!.publish(it) }
        )
    }

    private fun hasPreviousRotationFinished(): Boolean {
        // The current state of this method is to prevent any key rotations being started when any other one is in progress.
        // Same check is done on the Crypto worker side because if user quickly issues two key rotation commands after each other,
        // it will pass rest worker check as state manager was not yet populated.
        // On that note, if the logic is changed here, it should also be changed to match in the Crypto worker, see [CryptoRekeyBusProcessor]
        // for the equivalent method.
        stateManager.findByMetadataMatchingAll(
            listOf(
                MetadataFilter(
                    KeyRotationMetadataValues.STATUS_TYPE,
                    Operation.Equals,
                    KeyRotationRecordType.KEY_ROTATION
                )
            )
        ).forEach {
            if (it.value.metadata[KeyRotationMetadataValues.STATUS] != KeyRotationStatus.DONE) return false
        }
        return true
    }

    @Suppress("ThrowsCount")
    private fun validateInputParams(oldKeyAlias: String, newKeyAlias: String) {
        if (oldKeyAlias == newKeyAlias) throw InvalidInputDataException(
            "Cannot start key rotation. The old key alias must be different to the new key alias."
        )
        if (oldKeyAlias.isEmpty()) throw InvalidInputDataException(
            "Cannot start key rotation. The old key alias is not specified."
        )
        if (newKeyAlias.isEmpty()) throw InvalidInputDataException(
            "Cannot start key rotation. The new key alias is not specified."
        )
    }
}

/*
 * Do the start key rotation operation
 *
 * @param oldKeyAlias alias to replace
 * @param newKeyAlias alias to use
 * @param publishRequests callback to publish a list of kafka messages
 *
 * This is a top level function to make it easy to test without bothering with lifecycle and OSGi.
 */
fun doKeyRotation(
    oldKeyAlias: String,
    newKeyAlias: String,
    publishRequests: ((List<Record<String, KeyRotationRequest>>) -> Unit)
): ResponseEntity<KeyRotationResponse> {
    // We cannot validate oldKeyAlias or newKeyAlias early here on the client side of the RPC since
    // those values are considered sensitive.

    val requestId = UUID.randomUUID().toString()
    val keyRotationRequest = KeyRotationRequest(
        requestId,
        KeyType.UNMANAGED,
        oldKeyAlias,
        newKeyAlias,
        null
    )

    publishRequests(listOf(Record(REKEY_MESSAGE_TOPIC, requestId, keyRotationRequest, Instant.now().toEpochMilli())))
    return ResponseEntity.accepted(KeyRotationResponse(requestId, oldKeyAlias, newKeyAlias))
}

fun doManagedKeyRotation(
    tenantId: String,
    publishRequests: ((List<Record<String, KeyRotationRequest>>) -> Unit)
): ResponseEntity<ManagedKeyRotationResponse> {
    val requestId = UUID.randomUUID().toString()
    val keyRotationRequest = KeyRotationRequest(
        requestId,
        KeyType.MANAGED,
        null,
        null,
        tenantId
    )

    publishRequests(listOf(Record(REKEY_MESSAGE_TOPIC, requestId, keyRotationRequest, Instant.now().toEpochMilli())))
    return ResponseEntity.accepted(ManagedKeyRotationResponse(requestId, tenantId))
}

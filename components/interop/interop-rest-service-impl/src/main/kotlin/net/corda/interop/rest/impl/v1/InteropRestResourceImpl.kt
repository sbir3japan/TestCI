package net.corda.interop.rest.impl.v1

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.interop.endpoints.v1.InteropRestResource
import net.corda.libs.interop.endpoints.v1.common.withInteropManager
import net.corda.libs.interop.endpoints.v1.converter.convertToDto
import net.corda.libs.interop.endpoints.v1.types.CreateInteropIdentityRequest
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.management.InteropManagementService
import net.corda.rest.PluggableRestResource
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [PluggableRestResource::class])
internal class InteropRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = InteropManagementService::class)
    private val interopManagementService: InteropManagementService,
) : InteropRestResource, PluggableRestResource<InteropRestResource>, Lifecycle {

    private companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    // RestResource values
    override val targetInterface: Class<InteropRestResource> = InteropRestResource::class.java
    override fun getInterOpGroups(holdingidentityid: String?): List<UUID> {
        return listOf(UUID.randomUUID())
    }

    override fun createInterOpIdentity(
        createInteropIdentityRequest: CreateInteropIdentityRequest,
        holdingidentityid: String?
    ): ResponseEntity<CreateInteropIdentityRequest> {
        val restContext = CURRENT_REST_CONTEXT.get()
        val principal = restContext.principal

        val createInteropIdentityResult =
            withInteropManager(interopManagementService.interopManager, logger) {
                createInteropIdentity(createInteropIdentityRequest.convertToDto(principal))
            }

        return ResponseEntity.ok(createInteropIdentityResult)
    }

    override val protocolVersion = 1

    // Lifecycle
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<InteropRestResource>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> coordinator.updateStatus(LifecycleStatus.DOWN)
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> {
                        coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                        coordinator.postEvent(StopEvent(errored = true))
                    }

                    LifecycleStatus.UP -> {
                        // Receive updates to the REST and Messaging config
                        coordinator.createManagedResource(CONFIG_HANDLE) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                requiredKeys
                            )
                        }
                    }

                    else -> logger.debug { "Unexpected status: ${event.status}" }
                }
                coordinator.updateStatus(event.status)
            }
        }
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()

}
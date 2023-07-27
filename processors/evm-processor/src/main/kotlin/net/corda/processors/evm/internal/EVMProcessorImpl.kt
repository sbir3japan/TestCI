package net.corda.processors.evm.internal

import net.corda.processors.db.EVMProcessor
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.*

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.service.impl.bus.CryptoOpsBusProcessor
import net.corda.crypto.service.impl.bus.HSMRegistrationBusProcessor
import net.corda.libs.configuration.helper.getConfig
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG

import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory

import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.messaging.api.processor.RPCResponderProcessor

import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture


class EVMOpsBusProcessor() : RPCResponderProcessor<RpcOpsRequest, RpcOpsResponse> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

//        const val CLIENT_ID_REST_PROCESSOR = "rest.processor"
    }

    override fun onNext(request: RpcOpsRequest, respFuture: CompletableFuture<RpcOpsResponse>) {
        TODO("Not yet implemented")

        log.info(request.schema.toString(true))




    }




}


@Component(service = [EVMProcessor::class])
@Suppress("Unused", "LongParameterList")
class EVMProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
): EVMProcessor {

    val configKeys = setOf(
        MESSAGING_CONFIG
    )

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

//        const val CLIENT_ID_REST_PROCESSOR = "rest.processor"
    }

    private val dependentComponents = DependentComponents.of(
            ::configurationReadService,
        )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<EVMProcessorImpl>(dependentComponents, ::eventHandler)

    @Volatile
    private var dependenciesUp: Boolean = false

    override fun start(bootConfig: SmartConfig) {
        log.info("EVM processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
        // Wanted to put a state machine
        // Posting on a single thread

    }

    override fun stop() {
        log.info("EVM processor stopping.")
        lifecycleCoordinator.stop()
    }


    private fun startEthereumProcessor(config: SmartConfig) {

    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator){
        // work here
        log.info(event.toString())

        when (event){
            is StartEvent -> {
                log.trace("EVM Processor starting")
            }
            is StopEvent -> {
                log.trace("Stopping EVM Processor")
            }

            is BootConfigEvent -> {
                val bootstrapConfig = event.config
                log.trace("Bootstrapping {}", configurationReadService::class.simpleName)
                configurationReadService.bootstrapConfig(bootstrapConfig)
            }
            is RegistrationStatusChangeEvent -> {
                log.trace("Registering for configuration updates.")
                configurationReadService.registerComponentForUpdates(coordinator, configKeys)
            }

            is ConfigChangedEvent -> {
                log.trace("Config Changed Event")
                setStatus(LifecycleStatus.UP, coordinator)

                val ethereumConfig = event.config.getConfig(MESSAGING_CONFIG)

                coordinator.createManagedResource("RPC_OPS_SUBSCRIPTION") {
                    subscriptionFactory.createRPCSubscription(
                        rpcConfig = RPCConfig(
                            groupName = "evm.ops.rpc",
                            clientName = "evm.ops.rpc",
                            requestTopic = "evm.hsm.rpc.registration",
                            requestType = RpcOpsRequest::class.java,
                            responseType = RpcOpsResponse::class.java
                        ),
                        responderProcessor = EVMOpsBusProcessor(),
                        messagingConfig = ethereumConfig


                    )
                }






            }
        }
    }


    private fun setStatus(status: LifecycleStatus, coordinator: LifecycleCoordinator) {
        log.trace("Crypto processor is set to be $status")
        coordinator.updateStatus(status)
    }
    data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
}
package net.corda.components.rpc.internal

import net.corda.components.rbac.RBACSecurityManagerService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSSLSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings.Companion.MAX_CONTENT_LENGTH_DEFAULT_VALUE
import net.corda.httprpc.server.config.models.SsoSettings
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.ssl.SslCertReadService
import net.corda.httprpc.ssl.SslCertReadServiceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.service.PermissionServiceComponent
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger

@Suppress("LongParameterList")
internal class HttpRpcGatewayEventHandler(
    private val permissionServiceComponent: PermissionServiceComponent,
    private val configurationReadService: ConfigurationReadService,
    private val httpRpcServerFactory: HttpRpcServerFactory,
    private val rbacSecurityManagerService: RBACSecurityManagerService,
    private val sslCertReadServiceFactory: SslCertReadServiceFactory,
    private val dynamicRpcOps: List<PluggableRPCOps<out RpcOps>>,
) : LifecycleEventHandler {

    private companion object {
        val log = contextLogger()
        const val RPC_CONFIG = "corda.rpc"
        const val RPC_ADDRESS_CONFIG = "address"
        const val RPC_DESCRIPTION_CONFIG = "context.description"
        const val RPC_TITLE_CONFIG = "context.title"
        const val MAX_CONTENT_LENGTH_CONFIG = "maxContentLength"
        const val AZURE_CLIENT_ID_CONFIG = "sso.azureAd.clientId"
        const val AZURE_TENANT_ID_CONFIG = "sso.azureAd.tenantId"
        const val AZURE_CLIENT_SECRET_CONFIG = "sso.azureAd.clientSecret"
    }

    @VisibleForTesting
    internal var server: HttpRpcServer? = null

    @VisibleForTesting
    internal var sslCertReadService: SslCertReadService? = null

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    @VisibleForTesting
    internal var sub: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following ConfigurationReadService, PermissionServiceComponent and " +
                        "RBACSecurityManagerService and  for status updates.")

                registration?.close()
                registration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionServiceComponent>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<RBACSecurityManagerService>()
                    )
                )

                log.info("Starting permission service and RBAC security manager.")
                permissionServiceComponent.start()
                rbacSecurityManagerService.start()
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        log.info("Registration received UP status. Registering for configuration updates.")
                        // Http RPC Server can only be created when security manager and permission service are ready.
                        sub = configurationReadService.registerForUpdates(::onConfigurationUpdated)
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        log.info("Registration received DOWN status. Stopping the Http RPC Gateway.")
                        coordinator.postEvent(StopEvent())
                    }
                    LifecycleStatus.ERROR -> {
                        log.info("Registration received ERROR status. Stopping the Http RPC Gateway.")
                        coordinator.postEvent(StopEvent(true))
                    }
                }
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies.")
                registration?.close()
                registration = null
                sub?.close()
                sub = null
                permissionServiceComponent.stop()
                rbacSecurityManagerService.stop()
                server?.close()
                server = null
                sslCertReadService?.stop()
                sslCertReadService = null
                dynamicRpcOps.filterIsInstance<Lifecycle>().forEach { it.stop() }
            }
        }
    }

    private fun onConfigurationUpdated(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig>) {
        log.info("Gateway component received configuration update event, changedKeys: $changedKeys")

        if (RPC_CONFIG in changedKeys) {
            log.info("RPC config received. Recreating HTTP RPC Server.")

            createAndStartHttpRpcServer(currentConfigurationSnapshot[RPC_CONFIG]!!)
        }
    }

    private fun createAndStartHttpRpcServer(config: SmartConfig) {
        log.info("Stopping any running HTTP RPC Server and endpoints.")
        server?.stop()
        sslCertReadService?.stop()

        val keyStoreInfo = sslCertReadServiceFactory.create().let {
            this.sslCertReadService = it
            it.start()
            it.getOrCreateKeyStore()
        }

        val httpRpcSettings = HttpRpcSettings(
            address = NetworkHostAndPort.parse(config.getString(RPC_ADDRESS_CONFIG)),
            context = HttpRpcContext(
                version = "1",
                basePath = "/api",
                description = config.getString(RPC_DESCRIPTION_CONFIG),
                title = config.getString(RPC_TITLE_CONFIG)
            ),
            ssl = HttpRpcSSLSettings(keyStoreInfo.path, keyStoreInfo.password),
            sso = config.retrieveSsoOptions(),
            maxContentLength = config.retrieveMaxContentLength()
        )

        log.info("Starting HTTP RPC Server.")
        server = httpRpcServerFactory.createHttpRpcServer(
            rpcOpsImpls = dynamicRpcOps.toList(),
            rpcSecurityManager = rbacSecurityManagerService.securityManager,
            httpRpcSettings = httpRpcSettings
        ).also { it.start() }

        val numberOfRpcOps = dynamicRpcOps.filterIsInstance<Lifecycle>()
            .map { it.start() }
            .count()
        log.info("Started $numberOfRpcOps RPCOps that have lifecycle.")
    }

    private fun SmartConfig.retrieveSsoOptions(): SsoSettings? {
        return if (!hasPath(AZURE_CLIENT_ID_CONFIG) || !hasPath(AZURE_TENANT_ID_CONFIG)) {
            null
        } else {
            val clientId = getString(AZURE_CLIENT_ID_CONFIG)
            val tenantId = getString(AZURE_TENANT_ID_CONFIG)
            val clientSecret = AZURE_CLIENT_SECRET_CONFIG.let {
                if (hasPath(it)) {
                    getString(it)
                } else null
            }
            SsoSettings(AzureAdSettings(clientId, clientSecret, tenantId))
        }
    }

    private fun SmartConfig.retrieveMaxContentLength(): Int {
        return if (hasPath(MAX_CONTENT_LENGTH_CONFIG)) {
            getInt(MAX_CONTENT_LENGTH_CONFIG)
        } else {
            MAX_CONTENT_LENGTH_DEFAULT_VALUE
        }
    }
}
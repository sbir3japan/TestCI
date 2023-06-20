package net.corda.interop.aliasinfo.write.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.interop.aliasinfo.write.InteropAliasInfoWriteService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Component(service = [InteropAliasInfoWriteService::class])
class InteropAliasInfoWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : InteropAliasInfoWriteService {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleEventHandler = InteropAliasInfoWriteServiceEventHandler(
        configurationReadService
    )

    private val coordinatorName = LifecycleCoordinatorName.forComponent<InteropAliasInfoWriteService>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleEventHandler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun addInteropIdentity(
        holdingIdentityShortHash: String,
        interopGroupId: String,
        newIdentityName: MemberX500Name
    ) {
        TODO("Not yet implemented")
    }

    override fun start() {
        // Use debug rather than info
        log.info("Component starting")
        coordinator.start()
    }

    override fun stop() {
        // Use debug rather than info
        log.info("Component stopping")
        coordinator.stop()
    }
}

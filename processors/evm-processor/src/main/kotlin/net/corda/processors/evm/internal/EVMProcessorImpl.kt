package net.corda.processors.evm.internal

import net.corda.processors.db.EVMProcessor
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.createCoordinator

import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory






@Component(service = [EVMProcessor::class])
@Suppress("Unused", "LongParameterList")
class EVMProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
): EVMProcessor {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.)

//        const val CLIENT_ID_REST_PROCESSOR = "rest.processor"
    }

    private val dependentComponents = DependentComponents.of(

    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<EVMProcessorImpl>(dependentComponents, ::eventHandler)


    override fun start(bootConfig: SmartConfig) {

    }

    override fun stop() {
        log.info("REST processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator){
        // work here
        log.info(event)
    }
}
package net.corda.flow.maintenance

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.messaging.mediator.MediatorState
import net.corda.flow.state.impl.FlowCheckpointFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Factory for constructing event handlers for flow maintenance events
 */
@Component(service = [FlowMaintenanceHandlersFactory::class])
class FlowMaintenanceHandlersFactoryImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    avroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = CheckpointCleanupHandler::class)
    private val checkpointCleanupHandler: CheckpointCleanupHandler,
    @Reference(service = FlowCheckpointFactory::class)
    private val flowCheckpointFactory: FlowCheckpointFactory
) : FlowMaintenanceHandlersFactory {

    private val checkpointDeserializer = avroSerializationFactory.createAvroDeserializer({}, Checkpoint::class.java)
    private val mediatorStateDeserializer = avroSerializationFactory.createAvroDeserializer({}, MediatorState::class.java)

    override fun createScheduledTaskHandler(stateManager: StateManager, config: SmartConfig): FlowTimeoutTaskProcessor {
        return FlowTimeoutTaskProcessor(stateManager, config)
    }

    override fun createTimeoutEventHandler(
        stateManager: StateManager,
        config: SmartConfig
    ): TimeoutEventCleanupProcessor {
        return TimeoutEventCleanupProcessor(
            checkpointCleanupHandler,
            stateManager,
            mediatorStateDeserializer,
            checkpointDeserializer,
            flowCheckpointFactory,
            config
        )
    }
}

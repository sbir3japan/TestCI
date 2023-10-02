package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import net.corda.taskmanager.TaskManagerFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MultiSourceEventMediatorFactory::class])
class MultiSourceEventMediatorFactoryImpl(
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val stateManager: StateManager,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val taskManagerFactory: TaskManagerFactory
): MultiSourceEventMediatorFactory {

    @Activate
    constructor(
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        @Reference(service = StateManager::class)
        stateManager: StateManager,
        @Reference(service = LifecycleCoordinatorFactory::class)
        lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    ) : this(
        cordaAvroSerializationFactory,
        stateManager,
        lifecycleCoordinatorFactory,
        TaskManagerFactory.INSTANCE
    )

    override fun <K : Any, S : Any, E : Any> create(
        eventMediatorConfig: EventMediatorConfig<K, S, E>,
    ): MultiSourceEventMediator<K, S, E> {
        val stateSerializer = cordaAvroSerializationFactory.createAvroSerializer<S> { }
        val stateDeserializer = cordaAvroSerializationFactory.createAvroDeserializer(
            {},
            eventMediatorConfig.messageProcessor.stateValueClass
        )
        return MultiSourceEventMediatorImpl(
            eventMediatorConfig,
            stateSerializer,
            stateDeserializer,
            stateManager,
            taskManagerFactory.createThreadPoolTaskManager(
                name = "MultiSourceEventMediator",
                threadName = "multi-source-event-mediator",
                threads = 8
            ),
            lifecycleCoordinatorFactory,
        )
    }
}
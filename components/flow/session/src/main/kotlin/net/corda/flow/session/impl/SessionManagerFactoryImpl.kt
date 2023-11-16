package net.corda.flow.session.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.flow.session.SessionManager
import net.corda.flow.session.SessionManagerFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SessionManagerFactory::class])
class SessionManagerFactoryImpl @Activate constructor(
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val serializationFactory: CordaAvroSerializationFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : SessionManagerFactory {

    private companion object {
        private const val CLIENT_ID = "session-manager-publisher"
    }

    private var sessionManager: SessionManager? = null

    override fun create(stateManagerConfig: SmartConfig, messagingConfig: SmartConfig): SessionManager {
        return sessionManager ?: let {
            val newManager = createNew(stateManagerConfig, messagingConfig)
            sessionManager = newManager
            newManager
        }
    }

    private fun createNew(stateManagerConfig: SmartConfig, messagingConfig: SmartConfig) : SessionManager {
        val serializer = serializationFactory.createAvroSerializer<Any>()
        val deserializer = serializationFactory.createAvroDeserializer({}, Any::class.java)
        val stateManager = stateManagerFactory.create(stateManagerConfig).apply {
            start()
        }
        val publisher = publisherFactory.createPublisher(
            PublisherConfig(CLIENT_ID, transactional = false),
            messagingConfig
        ).apply {
            start()
        }
        val stateManagerHelper = StateManagerHelper(stateManager, serializer, deserializer)
        return SessionManagerImpl(stateManagerHelper, publisher)
    }
}
package net.corda.flow.testing.mediator

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MessagingClientFactory

class TestRpcClientFactory(
    private val id: String,
    private val messageBusConfig: SmartConfig,
): MessagingClientFactory {
    companion object {
        const val RPC_SEND_TIME_MS = "rpcSendTimeMs"
    }

    override fun create(config: MessagingClientConfig): MessagingClient {
        return TestRpcClient(
            id,
            messageBusConfig.getLong(RPC_SEND_TIME_MS),
        )
    }

    class TestRpcClient(
        override val id: String,
        private val sendTimeMs: Long,
    ) : MessagingClient {
        override fun send(message: MediatorMessage<*>): MediatorMessage<*>? {
            Thread.sleep(sendTimeMs)
            return MediatorMessage(null)
        }

        override fun close() {
            // Nothing to do here
        }
    }
}

package net.corda.ledger.consensual.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindTransaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindTransactionExternalEventFactory : AbstractConsensualLedgerExternalEventFactory<FindTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindTransactionParameters): Any {
        return FindTransaction(parameters.id, TransactionStatus.VERIFIED.value)
    }
}

@CordaSerializable
data class FindTransactionParameters(val id: String)

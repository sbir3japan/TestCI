package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.ledger.utxo.StateRef
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FetchFilteredTransactionsExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<FetchFilteredTransactionsParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)
    override fun createRequest(parameters: FetchFilteredTransactionsParameters): Any {
        return FetchFilteredTransactionsParameters(
            parameters.stateRefs
        )
    }
}

data class FetchFilteredTransactionsParameters(
    val stateRefs: List<StateRef>
)

package com.r3.corda.demo.interop.evm

import com.corda.evm.states.FungibleTokenState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*



data class CurrenciesStateResult(val id: UUID, val symbol: String, val balance: Long)

@Suppress("unused")
class ListCurrenciesFlow: ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    /**
     * This function lists all the currencies held by a member
     */
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        log.info("ListKudosFlow.call() called")

        // Queries the VNode's vault for unconsumed states and converts the result to a serializable DTO.
        val states = ledgerService.findUnconsumedStatesByExactType(FungibleTokenState::class.java,100, Instant.now())

        val key = memberLookup.myInfo().ledgerKeys.first()

        val results = states.results.map {
            CurrenciesStateResult(
                it.state.contractState.linearId,
                it.state.contractState.symbol,
                it.state.contractState.balances[key]!!
            )
        }

        return jsonMarshallingService.format(results)
    }
}
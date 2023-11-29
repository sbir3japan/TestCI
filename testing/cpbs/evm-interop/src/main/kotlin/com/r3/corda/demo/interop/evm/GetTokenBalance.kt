package com.r3.corda.demo.interop.evm

import com.corda.evm.states.FungibleTokenState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*


class GetTokenBalanceInput {
    val id: UUID? = null
}


@Suppress("unused")

class GetTokenBalance : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService


    /**
     * This function lists all the currencies held by a member
     */
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Get Token Balance Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetTokenBalanceInput::class.java)
            // Query the unconsumed states from the vault
            val states = ledgerService.findUnconsumedStatesByExactType(FungibleTokenState::class.java,100, Instant.ofEpochSecond(0))
            // Filter the states by the linearId
            val filteredState = states.results.filter { it.state.contractState.linearId == inputs.id }
            // Get the key for the member
            val key = memberLookup.myInfo().ledgerKeys.first()
            // Return the balance
            return filteredState[0].state.contractState.balances[key]!!.toString()

        } catch (e: Exception) {
            log.error("Error in Get Token Balance Flow: ${e.message}")
            throw e
        }
    }
}
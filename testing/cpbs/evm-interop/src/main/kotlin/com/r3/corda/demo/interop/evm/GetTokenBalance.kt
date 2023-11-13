package com.r3.corda.demo.interop.evm

import com.r3.corda.demo.interop.evm.state.FungibleTokenState
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
import java.time.temporal.Temporal
import java.util.*


class GetTokenBalanceInput {
    val id: UUID? = null
    val owner: String? = null
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

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Get Token Balance Flow...")
        try {

            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetTokenBalanceInput::class.java)

            val states = ledgerService.findUnconsumedStatesByExactType(FungibleTokenState::class.java,100, Instant.ofEpochSecond(0))

            // filter states by linearId

            val filteredState = states.results.filter { it.state.contractState.linearId == inputs.id }
            val ownerPubKey = memberLookup.lookup(MemberX500Name.parse(inputs.owner!!))?.ledgerKeys?.first()
            return filteredState[0].state.contractState.balances[ownerPubKey].toString()
        } catch (e: Exception) {
            log.error("Error in Get Token Balance Flow: ${e.message}")
            throw e
        }
    }
}
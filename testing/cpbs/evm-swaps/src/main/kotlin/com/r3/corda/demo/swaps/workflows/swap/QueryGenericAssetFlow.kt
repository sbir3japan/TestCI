package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.contracts.swap.AssetState
import com.r3.corda.demo.swaps.contracts.swap.GenericAssetQuery
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService

class QueryGenericAssetFlow : ClientStartableFlow {

    companion object {
        data class Args(val assetName: String)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, Args::class.java)

        val assets = ledgerService.query(GenericAssetQuery.GENERIC_ASSET_QUERY, AssetState::class.java)
            .setParameter("assetName", flowArgs.assetName)
            .execute()

        return assets.results.toString()
    }


}

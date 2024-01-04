package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.contracts.swap.AssetState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefTransformer
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderFactory

class QueryGenericAssetFlow : ClientStartableFlow {

    companion object {
        private const val GENERIC_ASSET_QUERY = "GenericAssetQuery"
        data class Args(val assetName: String)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, Args::class.java)

        val assets = ledgerService.query(GENERIC_ASSET_QUERY, AssetState::class.java)
            .setParameter("assetName", flowArgs.assetName)
            .execute()

        return assets.results.toString()
    }

    @Suppress("unused")
    class GenericAssetQuery : VaultNamedQueryFactory {
        override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
            vaultNamedQueryBuilderFactory.create(GENERIC_ASSET_QUERY)
                .whereJson(
                    "WHERE custom ->> 'AssetState.assetName' = :assetName"
                )
                .map(GenericAssetQueryTransformer())
                .register()
        }

        class GenericAssetQueryTransformer : VaultNamedQueryStateAndRefTransformer<AssetState, String> {
            override fun transform(state: StateAndRef<AssetState>, parameters: MutableMap<String, Any>): String {
                return state.state.contractState.assetName
            }
        }
    }
}

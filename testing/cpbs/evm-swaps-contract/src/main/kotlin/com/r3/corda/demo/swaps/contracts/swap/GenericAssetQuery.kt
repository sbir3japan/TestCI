package com.r3.corda.demo.swaps.contracts.swap

import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefTransformer
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderFactory

@Suppress("unused")
class GenericAssetQuery : VaultNamedQueryFactory {
    companion object {
        const val GENERIC_ASSET_QUERY = "GenericAssetQuery"
    }

    override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
        vaultNamedQueryBuilderFactory.create(GENERIC_ASSET_QUERY)
            .whereJson(
                "WHERE custom ->> 'AssetState.assetName' = :assetName"
            )
            .map(GenericAssetQueryTransformer())
            .register()
    }

    class GenericAssetQueryFilter: VaultNamedQueryStateAndRefFilter<AssetState> {
        override fun filter(data: StateAndRef<AssetState>, parameters: MutableMap<String, Any>): Boolean {
            return data.state.contractState.assetName == parameters["assetName"]
        }


    }

    class GenericAssetQueryTransformer : VaultNamedQueryStateAndRefTransformer<AssetState, String> {
        override fun transform(state: StateAndRef<AssetState>, parameters: MutableMap<String, Any>): String {
            return state.state.contractState.assetName
        }
    }
}
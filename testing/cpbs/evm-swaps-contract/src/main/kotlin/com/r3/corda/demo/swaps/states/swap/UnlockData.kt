package com.r3.corda.demo.swaps.states.swap

import com.r3.corda.demo.swaps.utils.trie.SimpleKeyValueStore
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.DigitalSignature

@CordaSerializable
data class UnlockData(
    val merkleProof: SimpleKeyValueStore,
    val validatorSignatures: List<DigitalSignature.WithKeyId>,
    val receiptsRootHash: String,
    val transactionReceipt: TransactionReceipt
)
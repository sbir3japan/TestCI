package com.r3.corda.demo.swaps.states.swap

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name


/**
 * Simple data structure describing the atomic swap details agreed upon by the involved parties.
 * Contains information to identify the sender, receiver, and assets being exchanged on both Corda and EVM networks as
 * well as a pool of approved validators/oracles (Corda nodes) which can verify and attest EVM events, and the minimum
 * number of validations required for the atomics swap protocol to succeed.
 */
@CordaSerializable
data class SwapTransactionDetails(
    val senderCordaName: MemberX500Name,
    val receiverCordaName: MemberX500Name,
//    val cordaAssetState: StateAndRef<OwnableState>,
    val approvedCordaValidators: List<MemberX500Name>,
    val minimumNumberOfEventValidations: Int,
//    val unlockEvent: IUnlockEventEncoder
)
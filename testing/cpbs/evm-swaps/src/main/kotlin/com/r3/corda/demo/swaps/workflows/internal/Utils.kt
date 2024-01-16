package com.r3.corda.demo.swaps.workflows.internal

import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigitalSignature
import org.utils.Numeric

class AggregateResult<TSource, TAccumulate>(val sequence: Collection<TSource>, val accumulator: TAccumulate) {
    operator fun component1(): Collection<TSource> = sequence
    operator fun component2(): TAccumulate = accumulator
}

/**
 * Conditional take-while aggregated sum of a sequence
 */
fun <TSource, TAccumulate> Sequence<TSource>.takeWhileAggregate(
    seed: TAccumulate,
    func: (TAccumulate, TSource) -> TAccumulate,
    predicate: (TAccumulate) -> Boolean
): AggregateResult<TSource, TAccumulate> {
    var accumulator = seed
    val result = mutableListOf<TSource>()
    for(it in this) {
        val tempAccumulator = func(accumulator, it)
        if (predicate(tempAccumulator)) {
            accumulator = tempAccumulator
            result.add(it)
        } else {
            break
        }
    }
    return AggregateResult(result, accumulator)
}

/**
 * Conditional take-until aggregated sum of a sequence
 */
fun <TSource, TAccumulate> Sequence<TSource>.takeUntilAggregate(
    seed: TAccumulate,
    func: (TAccumulate, TSource) -> TAccumulate,
    predicate: (TAccumulate) -> Boolean
): AggregateResult<TSource, TAccumulate> {
    var accumulator = seed
    val result = mutableListOf<TSource>()
    for(it in this) {
        result.add(it)
        accumulator = func(accumulator, it)
        if (predicate(accumulator)) {
            break
        }
    }
    return AggregateResult(result, accumulator)
}

/**
 * Flow extension to sign a receipt root
 */
@Suspendable
fun Flow.signReceiptRoot(
    evmService: EvmService,
    signingService: SigningService,
    signatureSpecService: SignatureSpecService,
    memberLookup: MemberLookup,
    rpcUrl: String,
    blockNumber: Int
) : DigitalSignature.WithKeyId {

    val block = evmService.getBlockByNumber(
        blockNumber.toBigInteger(),
        false,
        EvmOptions(rpcUrl,"")
    )

    val receiptsRootHash = Numeric.hexStringToByteArray(block.receiptsRoot)

    val myKey = memberLookup.myInfo().ledgerKeys.first()

    return signingService.sign(receiptsRootHash, myKey, signatureSpecService.defaultSignatureSpec(myKey)!!)
}

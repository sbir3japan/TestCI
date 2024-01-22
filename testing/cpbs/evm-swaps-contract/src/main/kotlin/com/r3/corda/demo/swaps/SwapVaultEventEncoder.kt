package com.r3.corda.demo.swaps

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import org.abi.DefaultFunctionEncoder
import org.abi.Utils.typeMap
import org.abi.datatypes.Address
import org.abi.datatypes.DynamicArray
import org.abi.datatypes.Type
import org.abi.datatypes.generated.Bytes32
import org.abi.datatypes.generated.Uint256
import org.crypto.Hash
import org.slf4j.LoggerFactory
import org.utils.Numeric
import java.math.BigInteger

@CordaSerializable
interface IUnlockEventEncoder {
    fun transferEvent(transactionId: SecureHash): EncodedEvent
    fun revertEvent(transactionId: SecureHash): EncodedEvent
}

@CordaSerializable
data class SwapVaultEventEncoder(
    val protocolAddress: String,
    val commitmentHash: ByteArray
) : IUnlockEventEncoder {
    companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun create(
            chainId: BigInteger,
            protocolAddress: String,
            owner: String,
            recipient: String,
            amount: BigInteger,
            tokenId: BigInteger,
            tokenAddress: String,
            signaturesThreshold: BigInteger,
            signers: List<String>
        ) : SwapVaultEventEncoder {
            val commitHash = commitmentHash(
                chainId,
                owner,
                recipient,
                amount,
                tokenId,
                tokenAddress,
                signaturesThreshold,
                signers
            )
            log.info("[DBG] Creating SwapVaultEventEncoder: chainId=$chainId, protocolAddress=$protocolAddress, owner=$owner, recipient=$recipient, amount=$amount, tokenId=$tokenId, tokenAddress=$tokenAddress, signaturesThreshold=$signaturesThreshold, signers=$signers, commitHash=$commitHash")
            return SwapVaultEventEncoder(
                protocolAddress,
                commitHash
            )
        }

        private fun commitmentHash(
            chainId: BigInteger,
            owner: String,
            recipient: String,
            amount: BigInteger,
            tokenId: BigInteger,
            tokenAddress: String,
            signaturesThreshold: BigInteger,
            signers: List<String>
        ): ByteArray {
            val parameters = listOf<Type<*>>(
                Uint256(chainId),
                Address(owner),
                Address(recipient),
                Uint256(amount),
                Uint256(tokenId),
                Address(tokenAddress),
                Uint256(signaturesThreshold),
                DynamicArray<Address>(Address::class.java, typeMap(signers, Address::class.java))
            )

            // Encode parameters using the DefaultFunctionEncoder
            val encodedParams = DefaultFunctionEncoder().encodeParameters(parameters)

            val bytes = Numeric.hexStringToByteArray(encodedParams)

            return Hash.sha3(bytes)
        }
    }

    public fun commitEvent(transactionId: SecureHash) = commitEvent(transactionId, Bytes32(commitmentHash))
    override fun transferEvent(transactionId: SecureHash) = transferEvent(transactionId, Bytes32(commitmentHash))
    override fun revertEvent(transactionId: SecureHash) = revertEvent(transactionId, Bytes32(commitmentHash))

    private fun commitEvent(transactionId: SecureHash, commitmentHash: Bytes32): EncodedEvent {
        return DefaultEventEncoder.encodeEvent(
            protocolAddress,
            "Commit(string,bytes32)",
            Indexed(transactionId.toHexString()),
            commitmentHash
        )
    }

    private fun transferEvent(transactionId: SecureHash, commitmentHash: Bytes32): EncodedEvent {
        return DefaultEventEncoder.encodeEvent(
            protocolAddress,
            "Transfer(string,bytes32)",
            Indexed(transactionId.toHexString()),
            commitmentHash
        )
    }

    private fun revertEvent(transactionId: SecureHash, commitmentHash: Bytes32): EncodedEvent {
        return DefaultEventEncoder.encodeEvent(
            protocolAddress,
            "Revert(string,bytes32)",
            Indexed(transactionId.toHexString()),
            commitmentHash
        )
    }
}

@CordaSerializable
data class Erc20TransferEventEncoder(
    private val tokenAddress: String,
    private val aliceAddress: String,
    private val bobAddress: String,
    private val amount: BigInteger
) : IUnlockEventEncoder {
    private val forwardTransferEvent = DefaultEventEncoder.encodeEvent(
        tokenAddress,
        "Transfer(address,address,uint256)",
        Indexed(aliceAddress),
        Indexed(bobAddress),
        amount
    )

    // Defines the encoding of an event that transfer an amount of 1 wei from Bob to Bob himself (signals revert)
    private val backwardTransferEvent = DefaultEventEncoder.encodeEvent(
        tokenAddress,
        "Transfer(address,address,uint256)",
        Indexed(aliceAddress),
        Indexed(aliceAddress),
        amount
    )

    override fun transferEvent(transactionId: SecureHash): EncodedEvent = forwardTransferEvent

    override fun revertEvent(transactionId: SecureHash): EncodedEvent = backwardTransferEvent
}

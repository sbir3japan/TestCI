package net.corda.flow.application.interop

import co.paralleluniverse.fibers.Suspendable
import java.math.BigInteger
import net.corda.flow.application.interop.external.events.EvmCallExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmCallExternalEventParams
import net.corda.flow.application.interop.external.events.EvmGetBalanceExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmGetBalanceExternalEventParamaters
import net.corda.flow.application.interop.external.events.EvmGetBlockByHashExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmGetBlockByHashParams
import net.corda.flow.application.interop.external.events.EvmGetBlockByNumberExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmGetBlockByNumberParams
import net.corda.flow.application.interop.external.events.EvmGetTransactionByHashEventFactoryParams
import net.corda.flow.application.interop.external.events.EvmGetTransactionByHashExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmTransactionExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmTransactionExternalEventParams
import net.corda.flow.application.interop.external.events.EvmTransactionReceiptExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmTransactionReceiptExternalEventParams
import net.corda.flow.application.interop.external.events.EvmWaitForTransactionExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmWaitForTransactionExternalEventFactoryParams
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.evm.Block
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.TransactionObject
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [EvmService::class, UsedByFlow::class],
    property = [CORDA_SYSTEM_SERVICE],
    scope = PROTOTYPE
)
class EvmServiceImpl @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
) : EvmService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun <T : Any> call(
        functionName: String,
        to: String,
        options: CallOptions,
        returnType: Type<T>,
        vararg parameters: Parameter<*>,
    ): T {
        return call(functionName, to, options, returnType, parameters.toList())
    }

    @Suspendable
    override fun <T : Any> call(
        functionName: String,
        to: String,
        options: CallOptions,
        returnType: Type<T>,
        parameters: List<Parameter<*>>,
    ): T {
        return try {
            val response = externalEventExecutor.execute(
                EvmCallExternalEventFactory::class.java,
                EvmCallExternalEventParams(
                    callOptions = options,
                    functionName = functionName,
                    to = to,
                    returnType = returnType,
                    parameters = parameters
                )
            )
            @Suppress("UNCHECKED_CAST")
            if (returnType.isList) {
                jsonMarshallingService.parseList(response, returnType.asClass()) as T
            } else if (returnType.isArray) {
                jsonMarshallingService.parseList(response, returnType.asClass()).toTypedArray() as T
            } else {
                jsonMarshallingService.parse(response, returnType.asClass()) as T
            }
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Incorrect type received for call on $functionName.", e)
        }
    }

    @Suspendable
    override fun transaction(
        functionName: String,
        to: String,
        options: TransactionOptions,
        vararg parameters: Parameter<*>,
    ): String {
        return transaction(functionName, to, options, parameters.toList())
    }

    @Suspendable
    override fun transaction(
        functionName: String,
        to: String,
        options: TransactionOptions,
        parameters: List<Parameter<*>>,
    ): String {
        return try {
            externalEventExecutor.execute(
                EvmTransactionExternalEventFactory::class.java,
                EvmTransactionExternalEventParams(
                    transactionOptions = options,
                    functionName = functionName,
                    to = to,
                    parameters = parameters,
                )
            )
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Incorrect type received for call on $functionName.", e)
        }
    }

    @Suspendable
    override fun getTransactionReceipt(
        hash: String,
        options: EvmOptions,
    ): TransactionReceipt {
        return try {
            externalEventExecutor.execute(
                EvmTransactionReceiptExternalEventFactory::class.java,
                EvmTransactionReceiptExternalEventParams(
                    options = options,
                    hash = hash,
                )
            )
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Wrong type returned for call to TransactionReceipt.", e)
        }
    }

    override fun getBlockByNumber(number: BigInteger, fullTransactionObject: Boolean, options: EvmOptions?): Block {
        return try {
            externalEventExecutor.execute(
                EvmGetBlockByNumberExternalEventFactory::class.java,
                EvmGetBlockByNumberParams(
                    options = options!!,
                    blockNumber = number.toString(),
                    fullTransactionObjects = fullTransactionObject,
                )
            )
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Wrong type returned for call to getBlockByNumber.", e)
        }
    }

    override fun getBlockByHash(hash: String, fullTransactionObject: Boolean, options: EvmOptions?): Block {
        return try {
            externalEventExecutor.execute(
                EvmGetBlockByHashExternalEventFactory::class.java,
                EvmGetBlockByHashParams(
                    options = options!!,
                    hash = hash,
                    fullTransactionObjects = fullTransactionObject,
                )
            )
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Wrong type returned for call to getBlockByNumber.", e)
        }
    }

    override fun getBalance(address: String, blockNumber: String, options: EvmOptions): BigInteger {
        return try {
            externalEventExecutor.execute(
                EvmGetBalanceExternalEventFactory::class.java,
                EvmGetBalanceExternalEventParamaters(
                    options = options,
                    address = address,
                    blockNumber = blockNumber,
                )
            )

        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Wrong type returned for call to getBalance.", e)
        }

    }

    override fun getTransactionByHash(hash: String, options: EvmOptions): TransactionObject {
        return try {
            externalEventExecutor.execute(
                EvmGetTransactionByHashExternalEventFactory::class.java,
                EvmGetTransactionByHashEventFactoryParams(
                    options = options,
                    hash = hash,
                )
            )
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Wrong type returned for call to getTransactionByHash.", e)
        }
    }

    override fun waitForTransaction(transactionHash: String, options: EvmOptions): TransactionReceipt {
        return try {
            externalEventExecutor.execute(
                EvmWaitForTransactionExternalEventFactory::class.java,
                EvmWaitForTransactionExternalEventFactoryParams(
                    options = options,
                    hash = transactionHash,
                )
            )
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Wrong type returned for call to waitForTransaction.", e)
        }
    }

}

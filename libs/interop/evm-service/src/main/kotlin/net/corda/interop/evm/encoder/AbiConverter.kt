package net.corda.interop.evm.encoder

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Uint
import org.web3j.utils.Numeric

import org.web3j.abi.datatypes.generated.Int104;
import org.web3j.abi.datatypes.generated.Int112;
import org.web3j.abi.datatypes.generated.Int120;
import org.web3j.abi.datatypes.generated.Int128;
import org.web3j.abi.datatypes.generated.Int136;
import org.web3j.abi.datatypes.generated.Int144;
import org.web3j.abi.datatypes.generated.Int152;
import org.web3j.abi.datatypes.generated.Int160;
import org.web3j.abi.datatypes.generated.Int168;
import org.web3j.abi.datatypes.generated.Int176;
import org.web3j.abi.datatypes.generated.Int184;
import org.web3j.abi.datatypes.generated.Int192;
import org.web3j.abi.datatypes.generated.Int200;
import org.web3j.abi.datatypes.generated.Int208;
import org.web3j.abi.datatypes.generated.Int216;
import org.web3j.abi.datatypes.generated.Int224;
import org.web3j.abi.datatypes.generated.Int232;
import org.web3j.abi.datatypes.generated.Int240;
import org.web3j.abi.datatypes.generated.Int248;
import org.web3j.abi.datatypes.generated.Int256;

import org.web3j.abi.datatypes.generated.Int72;
import org.web3j.abi.datatypes.generated.Int80;
import org.web3j.abi.datatypes.generated.Int88;
import org.web3j.abi.datatypes.generated.Int96;
import org.web3j.abi.datatypes.generated.Uint104;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.abi.datatypes.generated.Uint120;
import org.web3j.abi.datatypes.generated.Uint128;
import org.web3j.abi.datatypes.generated.Uint136;
import org.web3j.abi.datatypes.generated.Uint144;
import org.web3j.abi.datatypes.generated.Uint152;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint168;
import org.web3j.abi.datatypes.generated.Uint176;
import org.web3j.abi.datatypes.generated.Uint184;
import org.web3j.abi.datatypes.generated.Uint192;
import org.web3j.abi.datatypes.generated.Uint200;
import org.web3j.abi.datatypes.generated.Uint208;
import org.web3j.abi.datatypes.generated.Uint216;
import org.web3j.abi.datatypes.generated.Uint224;
import org.web3j.abi.datatypes.generated.Uint232;
import org.web3j.abi.datatypes.generated.Uint240;
import org.web3j.abi.datatypes.generated.Uint248;
import org.web3j.abi.datatypes.generated.Uint256;

import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint72;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.abi.datatypes.generated.Uint80;
import org.web3j.abi.datatypes.generated.Uint88;
import org.web3j.abi.datatypes.generated.Uint96;

import org.web3j.abi.datatypes.primitive.Int;


class AbiConverter {


    companion object {

        /**
         * Makes an RPC call to the Ethereum node and returns the JSON response as an RPCResponse object.
         *
         * @param type The solidity type that is being encoded.
         * @param value The value that is to be encoded.
         * @return Returns the Web3J data type that will be used to encode the function
         */
        fun getType(type: String, value: String): Any {
            return when (type.lowercase()) {
                "address" -> Address(value.removeSurrounding("\""))
                "bool", "boolean" -> Bool(value.toBoolean())
                "string" -> Utf8String(value)
                "bytes" -> DynamicBytes(value.toByteArray())
                "byte" -> org.web3j.abi.datatypes.primitive.Byte(value.toByte())
                "char" -> Char(value.toInt())
                "double" -> org.web3j.abi.datatypes.primitive.Double(value.toDouble())
                "float" -> org.web3j.abi.datatypes.primitive.Float(value.toFloat())
                "uint" -> Uint(value.toBigInteger())
                "int" -> org.web3j.abi.datatypes.primitive.Int(value.toInt())
                "long" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "short" -> org.web3j.abi.datatypes.primitive.Short(value.toShort())
                "uint8" -> Uint8(value.toBigInteger())
                "int8" -> org.web3j.abi.datatypes.primitive.Short(value.toShort())
                "uint16" -> org.web3j.abi.datatypes.primitive.Int(value.toInt())
                "int16" -> org.web3j.abi.datatypes.primitive.Int(value.toInt())
                "uint24" -> org.web3j.abi.datatypes.primitive.Int(value.toInt())
                "int24" -> org.web3j.abi.datatypes.primitive.Int(value.toInt())
                "uint32" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "int32" -> org.web3j.abi.datatypes.primitive.Int(value.toInt())
                "uint40" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "int40" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "uint48" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "int48" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "uint56" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "int56" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "uint64" -> Uint64(value.toBigInteger())
                "int64" -> org.web3j.abi.datatypes.primitive.Long(value.toLong())
                "uint72" -> Uint72(value.toBigInteger())
                "int72" -> Int72(value.toBigInteger())
                "uint80" -> Uint80(value.toBigInteger())
                "int80" -> Int80(value.toBigInteger())
                "uint88" -> Uint88(value.toBigInteger())
                "int88" -> Int88(value.toBigInteger())
                "uint96" -> Uint96(value.toBigInteger())
                "int96" -> Int96(value.toBigInteger())
                "uint104" -> Uint104(value.toBigInteger())
                "int104" -> Int104(value.toBigInteger())
                "uint112" -> Uint112(value.toBigInteger())
                "int112" -> Int112(value.toBigInteger())
                "uint120" -> Uint120(value.toBigInteger())
                "int120" -> Int120(value.toBigInteger())
                "uint128" -> Uint128(value.toBigInteger())
                "int128" -> Int128(value.toBigInteger())
                "uint136" -> Uint136(value.toBigInteger())
                "int136" -> Int136(value.toBigInteger())
                "uint144" -> Uint144(value.toBigInteger())
                "int144" -> Int144(value.toBigInteger())
                "uint152" -> Uint152(value.toBigInteger())
                "int152" -> Int152(value.toBigInteger())
                "uint160" -> Uint160(value.toBigInteger())
                "int160" -> Int160(value.toBigInteger())
                "uint168" -> Uint168(value.toBigInteger())
                "int168" -> Int168(value.toBigInteger())
                "uint176" -> Uint176(value.toBigInteger())
                "int176" -> Int176(value.toBigInteger())
                "uint184" -> Uint184(value.toBigInteger())
                "int184" -> Int184(value.toBigInteger())
                "uint192" -> Uint192(value.toBigInteger())
                "int192" -> Int192(value.toBigInteger())
                "uint200" -> Uint200(value.toBigInteger())
                "int200" -> Int200(value.toBigInteger())
                "uint208" -> Uint208(value.toBigInteger())
                "int208" -> Int208(value.toBigInteger())
                "uint216" -> Uint216(value.toBigInteger())
                "int216" -> Int216(value.toBigInteger())
                "uint224" -> Uint224(value.toBigInteger())
                "int224" -> Int224(value.toBigInteger())
                "uint232" -> Uint232(value.toBigInteger())
                "int232" -> Int232(value.toBigInteger())
                "uint240" -> Uint240(value.toBigInteger())
                "int240" -> Int240(value.toBigInteger())
                "uint248" -> Uint248(value.toBigInteger())
                "int248" -> Int248(value.toBigInteger())
                "uint256" -> Uint256(value.toBigInteger())
                "int256" -> Int256(value.toBigInteger())
                "bytes1" -> Numeric.hexStringToByteArray(value)
                "bytes2" -> Numeric.hexStringToByteArray(value)
                "bytes3" -> Numeric.hexStringToByteArray(value)
                "bytes4" -> Numeric.hexStringToByteArray(value)
                "bytes5" -> Numeric.hexStringToByteArray(value)
                "bytes6" -> Numeric.hexStringToByteArray(value)
                "bytes7" -> Numeric.hexStringToByteArray(value)
                "bytes8" -> Numeric.hexStringToByteArray(value)
                "bytes9" -> Numeric.hexStringToByteArray(value)
                "bytes10" -> Numeric.hexStringToByteArray(value)
                "bytes11" -> Numeric.hexStringToByteArray(value)
                "bytes12" -> Numeric.hexStringToByteArray(value)
                "bytes13" -> Numeric.hexStringToByteArray(value)
                "bytes14" -> Numeric.hexStringToByteArray(value)
                "bytes15" -> Numeric.hexStringToByteArray(value)
                "bytes16" -> Numeric.hexStringToByteArray(value)
                "bytes17" -> Numeric.hexStringToByteArray(value)
                "bytes18" -> Numeric.hexStringToByteArray(value)
                "bytes19" -> Numeric.hexStringToByteArray(value)
                "bytes20" -> Numeric.hexStringToByteArray(value)
                "bytes21" -> Numeric.hexStringToByteArray(value)
                "bytes22" -> Numeric.hexStringToByteArray(value)
                "bytes23" -> Numeric.hexStringToByteArray(value)
                "bytes24" -> Numeric.hexStringToByteArray(value)
                "bytes25" -> Numeric.hexStringToByteArray(value)
                "bytes26" -> Numeric.hexStringToByteArray(value)
                "bytes27" -> Numeric.hexStringToByteArray(value)
                "bytes28" -> Numeric.hexStringToByteArray(value)
                "bytes29" -> Numeric.hexStringToByteArray(value)
                "bytes30" -> Numeric.hexStringToByteArray(value)
                "bytes31" -> Numeric.hexStringToByteArray(value)
                "bytes32" -> Numeric.hexStringToByteArray(value)
                else -> {
                    throw CordaRuntimeException("Failed to find an appropriate EVM Type")
                }
            }
        }
    }


}

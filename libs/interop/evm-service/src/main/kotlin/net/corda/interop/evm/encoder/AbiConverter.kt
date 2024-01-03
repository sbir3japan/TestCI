package net.corda.interop.evm.encoder

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes1
import org.web3j.abi.datatypes.generated.Bytes10
import org.web3j.abi.datatypes.generated.Bytes11
import org.web3j.abi.datatypes.generated.Bytes12
import org.web3j.abi.datatypes.generated.Bytes13
import org.web3j.abi.datatypes.generated.Bytes14
import org.web3j.abi.datatypes.generated.Bytes15
import org.web3j.abi.datatypes.generated.Bytes16
import org.web3j.abi.datatypes.generated.Bytes17
import org.web3j.abi.datatypes.generated.Bytes18
import org.web3j.abi.datatypes.generated.Bytes19
import org.web3j.abi.datatypes.generated.Bytes2
import org.web3j.abi.datatypes.generated.Bytes20
import org.web3j.abi.datatypes.generated.Bytes21
import org.web3j.abi.datatypes.generated.Bytes22
import org.web3j.abi.datatypes.generated.Bytes23
import org.web3j.abi.datatypes.generated.Bytes24
import org.web3j.abi.datatypes.generated.Bytes25
import org.web3j.abi.datatypes.generated.Bytes26
import org.web3j.abi.datatypes.generated.Bytes27
import org.web3j.abi.datatypes.generated.Bytes28
import org.web3j.abi.datatypes.generated.Bytes29
import org.web3j.abi.datatypes.generated.Bytes3
import org.web3j.abi.datatypes.generated.Bytes30
import org.web3j.abi.datatypes.generated.Bytes31
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Bytes4
import org.web3j.abi.datatypes.generated.Bytes5
import org.web3j.abi.datatypes.generated.Bytes6
import org.web3j.abi.datatypes.generated.Bytes7
import org.web3j.abi.datatypes.generated.Bytes8
import org.web3j.abi.datatypes.generated.Bytes9
import org.web3j.abi.datatypes.generated.Int104
import org.web3j.abi.datatypes.generated.Int112
import org.web3j.abi.datatypes.generated.Int120
import org.web3j.abi.datatypes.generated.Int128
import org.web3j.abi.datatypes.generated.Int136
import org.web3j.abi.datatypes.generated.Int144
import org.web3j.abi.datatypes.generated.Int152
import org.web3j.abi.datatypes.generated.Int16
import org.web3j.abi.datatypes.generated.Int160
import org.web3j.abi.datatypes.generated.Int168
import org.web3j.abi.datatypes.generated.Int176
import org.web3j.abi.datatypes.generated.Int184
import org.web3j.abi.datatypes.generated.Int192
import org.web3j.abi.datatypes.generated.Int200
import org.web3j.abi.datatypes.generated.Int208
import org.web3j.abi.datatypes.generated.Int216
import org.web3j.abi.datatypes.generated.Int224
import org.web3j.abi.datatypes.generated.Int232
import org.web3j.abi.datatypes.generated.Int24
import org.web3j.abi.datatypes.generated.Int240
import org.web3j.abi.datatypes.generated.Int248
import org.web3j.abi.datatypes.generated.Int256
import org.web3j.abi.datatypes.generated.Int32
import org.web3j.abi.datatypes.generated.Int40
import org.web3j.abi.datatypes.generated.Int48
import org.web3j.abi.datatypes.generated.Int56
import org.web3j.abi.datatypes.generated.Int64
import org.web3j.abi.datatypes.generated.Int72
import org.web3j.abi.datatypes.generated.Int8
import org.web3j.abi.datatypes.generated.Int80
import org.web3j.abi.datatypes.generated.Int88
import org.web3j.abi.datatypes.generated.Int96
import org.web3j.abi.datatypes.generated.Uint104
import org.web3j.abi.datatypes.generated.Uint112
import org.web3j.abi.datatypes.generated.Uint120
import org.web3j.abi.datatypes.generated.Uint128
import org.web3j.abi.datatypes.generated.Uint136
import org.web3j.abi.datatypes.generated.Uint144
import org.web3j.abi.datatypes.generated.Uint152
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint160
import org.web3j.abi.datatypes.generated.Uint168
import org.web3j.abi.datatypes.generated.Uint176
import org.web3j.abi.datatypes.generated.Uint184
import org.web3j.abi.datatypes.generated.Uint192
import org.web3j.abi.datatypes.generated.Uint200
import org.web3j.abi.datatypes.generated.Uint208
import org.web3j.abi.datatypes.generated.Uint216
import org.web3j.abi.datatypes.generated.Uint224
import org.web3j.abi.datatypes.generated.Uint232
import org.web3j.abi.datatypes.generated.Uint24
import org.web3j.abi.datatypes.generated.Uint240
import org.web3j.abi.datatypes.generated.Uint248
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint40
import org.web3j.abi.datatypes.generated.Uint48
import org.web3j.abi.datatypes.generated.Uint56
import org.web3j.abi.datatypes.generated.Uint64
import org.web3j.abi.datatypes.generated.Uint72
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.abi.datatypes.generated.Uint80
import org.web3j.abi.datatypes.generated.Uint88
import org.web3j.abi.datatypes.generated.Uint96
import org.web3j.abi.datatypes.primitive.Byte
import org.web3j.abi.datatypes.primitive.Double
import org.web3j.abi.datatypes.primitive.Float
import org.web3j.utils.Numeric

@Suppress("MaxLineLength")
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
                "byte" -> Byte(value.toByte())
                "char" -> Char(value.toInt())
                "double" -> org.web3j.abi.datatypes.primitive.Double(value.toDouble())
                "float" -> Float(value.toFloat())
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

        fun getDynamicType(type: String, values: List<String>): Any {
            return when (type.lowercase()) {
                "address" -> DynamicArray(Address::class.java, values.map {  Address(it.removeSurrounding("\"")) })
                "bool", "boolean" -> DynamicArray(Bool::class.java, values.map { Bool(it.toBoolean()) })
                "string" -> DynamicArray(Utf8String::class.java, values.map { Utf8String(it) })
                "bytes" -> DynamicArray(DynamicBytes::class.java, values.map { DynamicBytes(it.toByteArray()) })
                "byte" -> DynamicArray(Byte::class.java, values.map { Byte(it.toByte()) })
                "char" -> DynamicArray(org.web3j.abi.datatypes.primitive.Char::class.java, values.map { org.web3j.abi.datatypes.primitive.Char(it.first()) })
                "double" -> DynamicArray(Double::class.java, values.map { Double(it.toDouble()) })
                "float" -> DynamicArray(Float::class.java, values.map { Float(it.toFloat()) })
                "uint" -> DynamicArray(Uint::class.java, values.map { Uint(it.toBigInteger()) })
                "int" -> DynamicArray(org.web3j.abi.datatypes.Int::class.java, values.map { org.web3j.abi.datatypes.Int(it.toBigInteger()) })
                "long" -> DynamicArray(org.web3j.abi.datatypes.primitive.Long::class.java, values.map { org.web3j.abi.datatypes.primitive.Long(it.toLong()) })
                "short" -> DynamicArray(org.web3j.abi.datatypes.primitive.Short::class.java, values.map { org.web3j.abi.datatypes.primitive.Short(it.toShort()) })
                "uint8" -> DynamicArray(Uint8::class.java, values.map { Uint8(it.toBigInteger()) })
                "int8" -> DynamicArray(Int8::class.java, values.map { Int8(it.toBigInteger()) })
                "uint16" -> DynamicArray(Uint16::class.java, values.map { Uint16(it.toBigInteger()) })
                "int16" -> DynamicArray(Int16::class.java, values.map { Int16(it.toBigInteger()) })
                "uint24" -> DynamicArray(Uint24::class.java, values.map { Uint24(it.toBigInteger()) })
                "int24" -> DynamicArray(Int24::class.java, values.map { Int24(it.toBigInteger()) })
                "uint32" -> DynamicArray(Uint32::class.java, values.map { Uint32(it.toBigInteger()) })
                "int32" -> DynamicArray(Int32::class.java, values.map { Int32(it.toBigInteger()) })
                "uint40" -> DynamicArray(Uint40::class.java, values.map { Uint40(it.toBigInteger()) })
                "int40" -> DynamicArray(Int40::class.java, values.map { Int40(it.toBigInteger()) })
                "uint48" -> DynamicArray(Uint48::class.java, values.map { Uint48(it.toBigInteger()) })
                "int48" -> DynamicArray(Int48::class.java, values.map { Int48(it.toBigInteger()) })
                "uint56" -> DynamicArray(Uint56::class.java, values.map { Uint56(it.toBigInteger()) })
                "int56" -> DynamicArray(Int56::class.java, values.map { Int56(it.toBigInteger()) })
                "uint64" -> DynamicArray(Uint64::class.java, values.map { Uint64(it.toBigInteger()) })
                "int64" -> DynamicArray(Int64::class.java, values.map { Int64(it.toBigInteger()) })
                "uint72" -> DynamicArray(Uint72::class.java, values.map { Uint72(it.toBigInteger()) })
                "int72" -> DynamicArray(Int72::class.java, values.map { Int72(it.toBigInteger()) })
                "uint80" -> DynamicArray(Uint80::class.java, values.map { Uint80(it.toBigInteger()) })
                "int80" -> DynamicArray(Int80::class.java, values.map { Int80(it.toBigInteger()) })
                "uint88" -> DynamicArray(Uint88::class.java, values.map { Uint88(it.toBigInteger()) })
                "int88" -> DynamicArray(Int88::class.java, values.map { Int88(it.toBigInteger()) })
                "uint96" -> DynamicArray(Uint96::class.java, values.map { Uint96(it.toBigInteger()) })
                "int96" -> DynamicArray(Int96::class.java, values.map { Int96(it.toBigInteger()) })
                "uint104" -> DynamicArray(Uint104::class.java, values.map { Uint104(it.toBigInteger()) })
                "int104" -> DynamicArray(Int104::class.java, values.map { Int104(it.toBigInteger()) })
                "uint112" -> DynamicArray(Uint112::class.java, values.map { Uint112(it.toBigInteger()) })
                "int112" -> DynamicArray(Int112::class.java, values.map { Int112(it.toBigInteger()) })
                "uint120" -> DynamicArray(Uint120::class.java, values.map { Uint120(it.toBigInteger()) })
                "int120" -> DynamicArray(Int120::class.java, values.map { Int120(it.toBigInteger()) })
                "uint128" -> DynamicArray(Uint128::class.java, values.map { Uint128(it.toBigInteger()) })
                "int128" -> DynamicArray(Int128::class.java, values.map { Int128(it.toBigInteger()) })
                "uint136" -> DynamicArray(Uint136::class.java, values.map { Uint136(it.toBigInteger()) })
                "int136" -> DynamicArray(Int136::class.java, values.map { Int136(it.toBigInteger()) })
                "uint144" -> DynamicArray(Uint144::class.java, values.map { Uint144(it.toBigInteger()) })
                "int144" -> DynamicArray(Int144::class.java, values.map { Int144(it.toBigInteger()) })
                "uint152" -> DynamicArray(Uint152::class.java, values.map { Uint152(it.toBigInteger()) })
                "int152" -> DynamicArray(Int152::class.java, values.map { Int152(it.toBigInteger()) })
                "uint160" -> DynamicArray(Uint160::class.java, values.map { Uint160(it.toBigInteger()) })
                "int160" -> DynamicArray(Int160::class.java, values.map { Int160(it.toBigInteger()) })
                "uint168" -> DynamicArray(Uint168::class.java, values.map { Uint168(it.toBigInteger()) })
                "int168" -> DynamicArray(Int168::class.java, values.map { Int168(it.toBigInteger()) })
                "uint176" -> DynamicArray(Uint176::class.java, values.map { Uint176(it.toBigInteger()) })
                "int176" -> DynamicArray(Int176::class.java, values.map { Int176(it.toBigInteger()) })
                "uint184" -> DynamicArray(Uint184::class.java, values.map { Uint184(it.toBigInteger()) })
                "int184" -> DynamicArray(Int184::class.java, values.map { Int184(it.toBigInteger()) })
                "uint192" -> DynamicArray(Uint192::class.java, values.map { Uint192(it.toBigInteger()) })
                "int192" -> DynamicArray(Int192::class.java, values.map { Int192(it.toBigInteger()) })
                "uint200" -> DynamicArray(Uint200::class.java, values.map { Uint200(it.toBigInteger()) })
                "int200" -> DynamicArray(Int200::class.java, values.map { Int200(it.toBigInteger()) })
                "uint208" -> DynamicArray(Uint208::class.java, values.map { Uint208(it.toBigInteger()) })
                "int208" -> DynamicArray(Int208::class.java, values.map { Int208(it.toBigInteger()) })
                "uint216" -> DynamicArray(Uint216::class.java, values.map { Uint216(it.toBigInteger()) })
                "int216" -> DynamicArray(Int216::class.java, values.map { Int216(it.toBigInteger()) })
                "uint224" -> DynamicArray(Uint224::class.java, values.map { Uint224(it.toBigInteger()) })
                "int224" -> DynamicArray(Int224::class.java, values.map { Int224(it.toBigInteger()) })
                "uint232" -> DynamicArray(Uint232::class.java, values.map { Uint232(it.toBigInteger()) })
                "int232" -> DynamicArray(Int232::class.java, values.map { Int232(it.toBigInteger()) })
                "uint240" -> DynamicArray(Uint240::class.java, values.map { Uint240(it.toBigInteger()) })
                "int240" -> DynamicArray(Int240::class.java, values.map { Int240(it.toBigInteger()) })
                "uint248" -> DynamicArray(Uint248::class.java, values.map { Uint248(it.toBigInteger()) })
                "int248" -> DynamicArray(Int248::class.java, values.map { Int248(it.toBigInteger()) })
                "uint256" -> DynamicArray(Uint256::class.java, values.map { Uint256(it.toBigInteger()) })
                "int256" -> DynamicArray(Int256::class.java, values.map { Int256(it.toBigInteger()) })
                "bytes1" -> DynamicArray(Bytes1::class.java, values.map { Bytes1(Numeric.hexStringToByteArray(it)) })
                "bytes2" -> DynamicArray(Bytes2::class.java, values.map { Bytes2(Numeric.hexStringToByteArray(it)) })
                "bytes3" -> DynamicArray(Bytes3::class.java, values.map { Bytes3(Numeric.hexStringToByteArray(it)) })
                "bytes4" -> DynamicArray(Bytes4::class.java, values.map { Bytes4(Numeric.hexStringToByteArray(it)) })
                "bytes5" -> DynamicArray(Bytes5::class.java, values.map { Bytes5(Numeric.hexStringToByteArray(it)) })
                "bytes6" -> DynamicArray(Bytes6::class.java, values.map { Bytes6(Numeric.hexStringToByteArray(it)) })
                "bytes7" -> DynamicArray(Bytes7::class.java, values.map { Bytes7(Numeric.hexStringToByteArray(it)) })
                "bytes8" -> DynamicArray(Bytes8::class.java, values.map { Bytes8(Numeric.hexStringToByteArray(it)) })
                "bytes9" -> DynamicArray(Bytes9::class.java, values.map { Bytes9(Numeric.hexStringToByteArray(it)) })
                "bytes10" -> DynamicArray(Bytes10::class.java, values.map { Bytes10(Numeric.hexStringToByteArray(it)) })
                "bytes11" -> DynamicArray(Bytes11::class.java, values.map { Bytes11(Numeric.hexStringToByteArray(it)) })
                "bytes12" -> DynamicArray(Bytes12::class.java, values.map { Bytes12(Numeric.hexStringToByteArray(it)) })
                "bytes13" -> DynamicArray(Bytes13::class.java, values.map { Bytes13(Numeric.hexStringToByteArray(it)) })
                "bytes14" -> DynamicArray(Bytes14::class.java, values.map { Bytes14(Numeric.hexStringToByteArray(it)) })
                "bytes15" -> DynamicArray(Bytes15::class.java, values.map { Bytes15(Numeric.hexStringToByteArray(it)) })
                "bytes16" -> DynamicArray(Bytes16::class.java, values.map { Bytes16(Numeric.hexStringToByteArray(it)) })
                "bytes17" -> DynamicArray(Bytes17::class.java, values.map { Bytes17(Numeric.hexStringToByteArray(it)) })
                "bytes18" -> DynamicArray(Bytes18::class.java, values.map { Bytes18(Numeric.hexStringToByteArray(it)) })
                "bytes19" -> DynamicArray(Bytes19::class.java, values.map { Bytes19(Numeric.hexStringToByteArray(it)) })
                "bytes20" -> DynamicArray(Bytes20::class.java, values.map { Bytes20(Numeric.hexStringToByteArray(it)) })
                "bytes21" -> DynamicArray(Bytes21::class.java, values.map { Bytes21(Numeric.hexStringToByteArray(it)) })
                "bytes22" -> DynamicArray(Bytes22::class.java, values.map { Bytes22(Numeric.hexStringToByteArray(it)) })
                "bytes23" -> DynamicArray(Bytes23::class.java, values.map { Bytes23(Numeric.hexStringToByteArray(it)) })
                "bytes24" -> DynamicArray(Bytes24::class.java, values.map { Bytes24(Numeric.hexStringToByteArray(it)) })
                "bytes25" -> DynamicArray(Bytes25::class.java, values.map { Bytes25(Numeric.hexStringToByteArray(it)) })
                "bytes26" -> DynamicArray(Bytes26::class.java, values.map { Bytes26(Numeric.hexStringToByteArray(it)) })
                "bytes27" -> DynamicArray(Bytes27::class.java, values.map { Bytes27(Numeric.hexStringToByteArray(it)) })
                "bytes28" -> DynamicArray(Bytes28::class.java, values.map { Bytes28(Numeric.hexStringToByteArray(it)) })
                "bytes29" -> DynamicArray(Bytes29::class.java, values.map { Bytes29(Numeric.hexStringToByteArray(it)) })
                "bytes30" -> DynamicArray(Bytes30::class.java, values.map { Bytes30(Numeric.hexStringToByteArray(it)) })
                "bytes31" -> DynamicArray(Bytes31::class.java, values.map { Bytes31(Numeric.hexStringToByteArray(it)) })
                "bytes32" -> DynamicArray(Bytes32::class.java, values.map { Bytes32(Numeric.hexStringToByteArray(it)) })
                else -> {
                    throw CordaRuntimeException("Failed to find an appropriate EVM Type")
                }
            }
        }
    }


}

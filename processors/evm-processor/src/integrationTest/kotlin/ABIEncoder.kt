import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.*
import org.web3j.utils.Numeric
import java.math.BigInteger
import org.web3j.crypto.Hash

class ABIEncoder {

    // Encodes The Function Signature With Paramaters
    fun encodeFunctionSignature(method: ABIContractFunction, params: List<Type<*>>): String{
        val methodString = jsonInterfaceMethodToString(method);
        println("methodString '${methodString}'")
        val hashedString = Hash.sha3String(methodString).slice(IntRange(0,9))
        println("Hashed String: ${hashedString}")
        println("params: ")
        val encodedParams = encodeParameters(params)
        println("Encoded params: ${encodedParams}")
        return hashedString+encodedParams
    }


    fun flattenTypes(includeTuple: Boolean, puts: List<ABIContractInput>): List<String> {
        val types = mutableListOf<String>()
        puts.forEach { param ->
            if (!param.components.isNullOrEmpty()) {
                if (!param.type.startsWith("tuple")) {
                    throw Error("Invalid value given \"${param.type}\". Error: components found but type is not tuple.")
                }
                val arrayBracket = param.type.indexOf('[')
                val suffix = if (arrayBracket >= 0) param.type.substring(arrayBracket) else ""
                val result = flattenTypes(includeTuple, param.components)
                if (includeTuple) {
                    types.add("tuple(${result.joinToString(",")})$suffix")
                } else  {
                    types.add("(${result.joinToString(",")})$suffix")
                }
            } else {
                types.add(param.type)
            }
        }

        return types
    }

    private fun jsonInterfaceMethodToString(method: ABIContractFunction): String{
        val types = flattenTypes(false, method.inputs);
        return "${method.name}(${types.joinToString(",")})"
    }


    private fun isDynamic(parameter: Type<*>): Boolean {
        return parameter is DynamicBytes
                || parameter is Utf8String
                || parameter is DynamicArray<*>
                || (parameter is StaticArray<*> && DynamicStruct::class.java.isAssignableFrom(parameter.componentType))
    }


    private fun toByteArray(numericType: NumericType): ByteArray {
        val value = numericType.value
        if (numericType is Ufixed || numericType is Uint) {
            if (value.bitLength() == Type.MAX_BIT_LENGTH) {
                // As BigInteger is signed, if we have a 256 bit value, the resultant byte array
                // will contain a sign byte in its MSB, which we should ignore for this unsigned
                // integer type.
                val byteArray = ByteArray(Type.MAX_BYTE_LENGTH)
                System.arraycopy(value.toByteArray(), 1, byteArray, 0, Type.MAX_BYTE_LENGTH)
                return byteArray
            }
        }
        return value.toByteArray()
    }

    private fun getPaddingValue(numericType: NumericType): Byte {
        if (numericType.value.signum() == -1) {
            return 0xFF.toByte()
        } else {
            return 0.toByte()
        }
    }

    private fun encodeNumeric(numericType: NumericType): String {
        val rawValue = toByteArray(numericType)
        val paddingValue = getPaddingValue(numericType)
        val paddedRawValue = ByteArray(Type.MAX_BYTE_LENGTH)
        if (paddingValue != 0.toByte()) {
            for (i in paddedRawValue.indices) {
                paddedRawValue[i] = paddingValue
            }
        }

        System.arraycopy(
            rawValue, 0, paddedRawValue, Type.MAX_BYTE_LENGTH - rawValue.size, rawValue.size)
        return Numeric.toHexStringNoPrefix(paddedRawValue)
    }

    private fun encodeParameters(parameters: List<Type<*>>): String {
        val result = StringBuilder()
        var dynamicDataOffset = parameters.size * Type.MAX_BYTE_LENGTH
        val dynamicData = StringBuilder()
        for (parameter in parameters) {
            val encodedValue = TypeEncoder.encode(parameter)
            if (isDynamic(parameter)) {
                val encodedDataOffset = encodeNumeric(Uint(BigInteger.valueOf(dynamicDataOffset.toLong())))
                result.append(encodedDataOffset)
                dynamicData.append(encodedValue)
                dynamicDataOffset += encodedValue.length shr 1
            } else {
                result.append(encodedValue)
            }
        }
        result.append(dynamicData)
        return result.toString()
    }

}
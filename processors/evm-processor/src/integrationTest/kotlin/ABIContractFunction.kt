data class ABIContractFunction(
    val inputs: List<ABIContractInput>,
    val outputs: List<ABIContractInput>,
    val name: String,
    val stateMutability: String,
    val type: String
)


data class ABIContractInput(
    val name: String,
    val internalType: String,
    val type: String,
    // optional
    val components: List<ABIContractInput>? = null
)

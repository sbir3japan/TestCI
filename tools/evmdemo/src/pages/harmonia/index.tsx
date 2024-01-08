import React from "react"
import { CommitWithTokenFlow, IssueGenericAssetFlow, RequestLockByEventFlow,SignDraftTransactionByIdFlow, claimCommitment, unlockAssetFlow } from "@/api/gateway"
import {Web3} from "web3"
const Harmonia = () => {


    const StringToHex = (str) => {
        const web3 = new Web3()
        return web3.utils.fromAscii(str)
    }

    // rrandom string funciton
    const randomString = (length) => {
        let result = '';
        const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
        const charactersLength = characters.length;
        
        for (let i = 0; i < length; i++) {
            result += characters.charAt(Math.floor(Math.random() * charactersLength));
        }

        return result;
    }
    const start = async () => {
        // const res = await RequestLockByEventFlow(
        //     "C04E17844E72",
        //     "0xF382FA9E9FEDDCCB8AA8A440EE5D47FE6F687B72167384D9265E25B6DDB3F68D",
        //     "com.r3.corda.demo.swaps.contracts.swap.AssetState",
        //     "CN=Eve, OU=Application, O=NordVPN, L=Athens, C=GR",
        //     1,
        //     1337,
        //     process.env.SWAP_VAULT_ADDRESS,
        //     "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
        //     "0x627306090abab3a6e1400e9345bc60c78a8bef57",
        //     process.env.ERC20_ADDRESS,
        //     100,
        //     100
        //     )

        //     console.log("res: ",res)

        const holdingId1 = "C04E17844E72"

        const res = await IssueGenericAssetFlow(
            holdingId1,
            randomString(10),
        )

        const transactionId = res
        // CN=EVM, OU=Application, O=Ethereum, L=Brussels, C=BE => 0x627306090abaB3A6e1400e9345bC60c78a8BEf57

        const output = await RequestLockByEventFlow(
            holdingId1,
            transactionId,
            "com.r3.corda.demo.swaps.contracts.swap.AssetState",
            "CN=EVM, OU=Application, O=Ethereum, L=Brussels, C=BE",
            1,
            1337,
            process.env.SWAP_VAULT_ADDRESS,
            "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73",
            "0x627306090abaB3A6e1400e9345bC60c78a8BEf57",
            process.env.ERC20_ADDRESS,
            100,
            0
        )

            console.log("output: ",output)


            const output2 = await CommitWithTokenFlow(
                holdingId1,
                output,
                "http://host.docker.internal:8545",
                process.env.ERC20_ADDRESS,
                "0x627306090abaB3A6e1400e9345bC60c78a8BEf57",
                100,
                1,
                ["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"],
                "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73",
                process.env.SWAP_VAULT_ADDRESS,
                ""
            )

            console.log("output2: ",output2)


            const draftTxOutput = await SignDraftTransactionByIdFlow(holdingId1, output)
            console.log("draftTxOutput: ",draftTxOutput)

            const commitment = await claimCommitment(holdingId1, output, "http://host.docker.internal:8545",["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"],process.env.SWAP_VAULT_ADDRESS,"")
            console.log("commitment: ",commitment)
            const jsonCommitment = JSON.parse(commitment)
            const transactionReceipt = jsonCommitment.transactionReceipt
            const blockNumber = transactionReceipt.blockNumber
            const transactionIndex = transactionReceipt.transactionIndex



            const unlockAssetOutput = await  unlockAssetFlow(holdingId1,output, blockNumber, transactionIndex)
            console.log("unlockAssetOutput: ",unlockAssetOutput)










    }

    const getInfo = async () => {
        // transactionId,
        // assetType,
        // lockToRecipient,
        // signaturesThreshold,
        // chainId,
        // protocolAddress,
        // evmSender,
        // evmRecipient,
        // tokenAddress,
        // amount,
        // tokenId

    }
    React.useEffect(() => {
        getInfo()
    }, [])
    return (
        <div>
        <h1>Harmonia!</h1>
        <button onClick={start}>Start Process</button>
        </div>
    )
}

export default Harmonia
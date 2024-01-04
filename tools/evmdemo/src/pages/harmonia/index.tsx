import React from "react"
import { RequestLockByEventFlow } from "@/api/gateway"
import {Web3} from "web3"
const Harmonia = () => {


    const StringToHex = (str) => {
        const web3 = new Web3()
        return web3.utils.fromAscii(str)
    }

    const start = async () => {
        const res = await RequestLockByEventFlow(
            "2CD775D033E7",
            "0xF382FA9E9FEDDCCB8AA8A440EE5D47FE6F687B72167384D9265E25B6DDB3F68D",
            "com.r3.corda.demo.swaps.contracts.swap.AssetState",
            "98BA3D01227D",
            1,
            1337,
            process.env.SWAP_VAULT_ADDRESS,
            "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
            "0x627306090abab3a6e1400e9345bc60c78a8bef57",
            process.env.ERC20_ADDRESS,
            100,
            100
            )

            console.log("res: ",res)
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
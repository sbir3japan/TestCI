const {Web3} = require('web3');
const web3 = new Web3('http://127.0.0.1:8545');
const abi = require('./abi.json')


const deployContract = async () => {
    const privKey = "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"
    const mainAccount = web3.eth.accounts.privateKeyToAccount(privKey)
    const contract = new web3.eth.Contract(abi.abi);
    const bytecode = abi.bytecode;
    const gasLimit = 6721975;
    const gasPrice = 20000000000;
    const hexGasLimit = web3.utils.toHex(gasLimit);
    const hexGasPrice = web3.utils.toHex(gasPrice);
    // deploy contract using sendRawTransaction

    const contractInstance = await contract.deploy({
        data: bytecode,
        arguments:[],
    }).encodeABI();

    const tx = {
        from: mainAccount.address,
        gas: hexGasLimit,
        gasPrice: hexGasPrice,
        data: contractInstance
    }

    const signedTx = await web3.eth.accounts.signTransaction(tx, privKey);

    const receipt = await web3.eth.sendSignedTransaction(signedTx.rawTransaction);
    console.log(receipt)
    console.log("++++++++++++++++++++++++++")
    console.log("++++++++++++++++++++++++++")
    console.log("contract address: ",receipt.contractAddress);
    console.log("++++++++++++++++++++++++++")
    console.log("++++++++++++++++++++++++++")


    // instantiate contract
    const contractInst = new web3.eth.Contract(abi.abi);


    // create token
    const token = contractInst.methods.createToken(mainAccount.address, 1000000,"Hello World").encodeABI();

    const tx2 = {
        from: mainAccount.address,
        gas: hexGasLimit,
        gasPrice: hexGasPrice,
        data: token,
        to: receipt.contractAddress
    }

    const signedTx2 = await web3.eth.accounts.signTransaction(tx2, privKey);

    const receipt2 = await web3.eth.sendSignedTransaction(signedTx2.rawTransaction);
console.log(receipt2)
    // get balance
    const balance = contractInst.methods.balanceOf(mainAccount.address,1).encodeABI();
    const decodedBalance = await web3.eth.call({
        to: receipt.contractAddress,
        data: balance
    })

    console.log("++++++++++++++++++++++++++")
    console.log("++++++++++++++++++++++++++")
    console.log("balance: ",decodedBalance);
    console.log("++++++++++++++++++++++++++")
    console.log("++++++++++++++++++++++++++")

    // exit process with contractAddress as the output
    process.exit(0);
}

deployContract()
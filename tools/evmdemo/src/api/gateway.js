const uuid = require("uuid");

const getResponse = async (url, requestOptions) => {
  const response = await fetch(url, requestOptions);
  const data = await response.json();
  if (!response.ok) {
    const error = data?.message || response.status;
    return await Promise.reject(error);
  }
  return data;
};
const fetchTokens = async (activeHoldingId) => {
  const url = `https://localhost:8888/api/v1/flow/${activeHoldingId}`;
  const clientRequestId = uuid.v4();
  const options = {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: "Basic " + Buffer.from("admin:admin").toString("base64"),
    },
    body: JSON.stringify({
      startFlow: {
        clientRequestId,
        flowClassName: "com.r3.corda.demo.interop.evm.ListCurrenciesFlow",
        requestBody: {},
      },
    }),
  };

  const response = await fetch(url, options);
  const data = await response.json();
  console.log(data);

  const fetchRequestOptions = {
    method: "GET",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: "Basic " + Buffer.from("admin:admin").toString("base64"),
    },
  };

  // wait 1 second
  await new Promise((r) => setTimeout(r, 1000));
  let found = false;
  let output = {};
  while (!found) {
    const res = await getResponse(
      `https://localhost:8888/api/v1/flow/${activeHoldingId}/${clientRequestId}`,
      fetchRequestOptions
    );
    console.log("📱 PINGING STATUS: ", res);
    if (res.flowStatus !== "RUNNING" && res.flowStatus !== "START_REQUESTED") {
      found = true;
      output = res;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  console.log("📱 FLOW RESULT: ", output.flowResult);
  const parsedOutput = JSON.parse(output.flowResult);
  return parsedOutput[parsedOutput.length - 1];
};
const postFlowData = async (price, id, amount, activeHoldingId) => {
  const username = "admin";
  const password = "admin";
  const clientRequestId = uuid.v4();
  const requestOptions = {
    // crossDomain:true,
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Authorization: "Basic " + btoa(username + ":" + password),
    },

    body: JSON.stringify({
      clientRequestId,
      flowClassName: "com.r3.corda.demo.interop.evm.EvmDemoFlow",
      requestBody: JSON.stringify({
        rpcUrl: "http://localhost:8545",
        buyerAddress: "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73",
        contractAddress: process.env.FRACTIONAL_CONTRACT_ADDRESS,
        sellerAddress: "0xf675aEfFf9019E580c61Dbaf1f22BD43249143A3",
        fractionPurchased: amount,
        purchasePrice: price,
        id,
      }),
    }),
    // credentials: "include",
    // mode: "no-cors",
    // redirect: "follow",
    // referrerPolicy: "no-referrer",
  };

  console.log("Request Options: ", JSON.parse(requestOptions.body));

  console.log(
    "Request Body: ",
    JSON.parse(JSON.parse(requestOptions.body).requestBody)
  );
  const response = await fetch(
    `https://localhost:8888/api/v1/flow/${activeHoldingId}`,
    requestOptions
  );
  const data = await response.json();
  if (!response.ok) {
    const error = data?.message || response.status;
    return await Promise.reject(error);
  }
  console.log("🚀 Made Request: ", data);

  const fetchRequestOptions = {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Authorization: "Basic " + btoa(username + ":" + password),
    },
  };

  // wait 1 second
  await new Promise((r) => setTimeout(r, 1000));
  let found = false;
  let output = {};
  while (!found) {
    const res = await getResponse(
      `https://localhost:8888/api/v1/flow/${activeHoldingId}/${clientRequestId}`,
      fetchRequestOptions
    );
    console.log("📱 PINGING STATUS: ", res);
    if (res.flowStatus !== "RUNNING" && res.flowStatus !== "START_REQUESTED") {
      found = true;
      output = res;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return JSON.parse(output.flowResult).hash;
};

const buildTransaction = async (flowClassName,activeHoldingId, requestBody) => {
  const username = "admin";
  const password = "admin";
  const clientRequestId = uuid.v4();
  const requestOptions = {
    // crossDomain:true,
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Authorization: "Basic " + btoa(username + ":" + password),
    },

    body: JSON.stringify({
      clientRequestId,
      flowClassName: flowClassName,
      requestBody
    }),
  };

  console.log("Request Options: ", JSON.parse(requestOptions.body));

  console.log(
    "Request Body: ",
    JSON.parse(JSON.parse(requestOptions.body).requestBody)
  );
  const response = await fetch(
    `https://localhost:8888/api/v1/flow/${activeHoldingId}`,
    requestOptions
  );
  const data = await response.json();
  if (!response.ok) {
    const error = data?.message || response.status;
    return await Promise.reject(error);
  }
  console.log("🚀 Made Request: ", data);

  const fetchRequestOptions = {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Authorization: "Basic " + btoa(username + ":" + password),
    },
  };

  // wait 1 second
  await new Promise((r) => setTimeout(r, 1000));
  let found = false;
  let output = {};
  while (!found) {
    const res = await getResponse(
      `https://localhost:8888/api/v1/flow/${activeHoldingId}/${clientRequestId}`,
      fetchRequestOptions
    );
    console.log("📱 PINGING STATUS: ", res);
    if (res.flowStatus !== "RUNNING" && res.flowStatus !== "START_REQUESTED") {
      found = true;
      output = res;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return JSON.parse(output.flowResult).hash;
}



const signTransactionByIdFlow = async (activeHoldingId, transactionId) => {
  const requestBody = JSON.stringify({
    transactionId
  })
  return await buildTransaction("com.r3.corda.demo.swaps.workflows.atomic.SignTransactionByIdFlow",activeHoldingId, requestBody)
}



const collectBlockSignatures = async (activeHoldingId, transactionId, blockNumber, blocking) => {
  const requestBody = JSON.stringify({
    transactionId,
    blockNumber,
    blocking
  })
  return await buildTransaction("com.r3.corda.demo.swaps.workflows.atomic.CollectBlockSignaturesFlow",activeHoldingId, requestBody)
}

const unlockAssetFlow = async (activeHoldingId, transactionId, blockNumber, transactionIndex) => {
  const requestBody = JSON.stringify({
    transactionId,
    blockNumber,
    transactionIndex
  })
  return await buildTransaction("com.r3.corda.demo.swaps.workflows.atomic.UnlockAssetFlow",activeHoldingId, requestBody)
}


// DemoDraftAssetSwapFlow

// flow start DemoDraftAssetSwapFlow transactionId: "F382FA9E9FEDDCCB8AA8A440EE5D47FE6F687B72167384D9265E25B6DDB3F68D", outputIndex: 0, recipient: Alice, validator: Charlie, signer: "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"

const DemoDraftAssetSwapFlow = async (activeHoldingId, transactionId, outputIndex, recipient, validator, signer) => {
  const requestBody = JSON.stringify({
    transactionId,
    outputIndex,
    recipient,
    validator,
    signer
  })
  return await buildTransaction("com.r3.corda.demo.swaps.workflows.atomic.DemoDraftAssetSwapFlow",activeHoldingId, requestBody)
}


// "flowClassName": "com.r3.corda.demo.swaps.workflows.swap.RequestLockByEventFlow",
// "requestBody": {
//     "transactionId": "",
// "assetType": "com.r3.corda.demo.swaps.contracts.swap.AssetState",
// "lockToRecipient": "$HOLDING_ID",
// "signaturesThreshold": "1",
// "chainId": "",
// "protocolAddress": "",
// "evmSender": "",
// "evmRecipient": "",
// "tokenAddress": "",
// "amount": "",
// "tokenId": ""
// }
// }

const RequestLockByEventFlow = async (activeHoldingId, transactionId, assetType, lockToRecipient, signaturesThreshold, chainId, protocolAddress, evmSender, evmRecipient, tokenAddress, amount, tokenId) => {
  const requestBody = JSON.stringify({
    transactionId,
    assetType,
    lockToRecipient,
    signaturesThreshold,
    chainId,
    protocolAddress,
    evmSender,
    evmRecipient,
    tokenAddress,
    amount,
    tokenId
  })
  return await buildTransaction("com.r3.corda.demo.swaps.workflows.swap.RequestLockByEventFlow",activeHoldingId, requestBody)
}




// fetch this: https://localhost:8888/api/v1/virtualnode
const fetchNetworkParticipants = async () => {
  const username = "admin";
  const password = "admin";
  const requestOptions = {
    // crossDomain:true,
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Authorization: "Basic " + btoa(username + ":" + password),
    },
  };
  const request = await fetch(
    "https://localhost:8888/api/v1/virtualnode",
    requestOptions
  );
  const data = await request.json();
  console.log("📱 NETWORK PARTICIPANTS: ", data);
  return data.virtualNodes;
};





module.exports = {
  postFlowData,
  fetchTokens,
  fetchNetworkParticipants,
  signTransactionByIdFlow,
  collectBlockSignatures,
  unlockAssetFlow,
  DemoDraftAssetSwapFlow,
  RequestLockByEventFlow
};

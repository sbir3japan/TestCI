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
};

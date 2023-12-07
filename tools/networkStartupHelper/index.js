

const fetch = require('node-fetch');
const https = require('https');
 const fetchRequestOptions = {
    method: "GET",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: "Basic " + Buffer.from("admin:admin").toString("base64"),
      agent: new https.Agent({
        rejectUnauthorized: false,
        }),

    },
  };
process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

const networkHasStarted = async () => {
    while(true){


    try {
    console.log('Checking if network has started...')
        const response = await fetch('https://localhost:8888/api/v1/virtualnode', fetchRequestOptions);
        const json = await response.json();
         console.log(json)
        if(json.virtualNodes){
            console.log('Network has started')
            process.exit(0);
        }
    }catch(error){
    console.log(error)
        console.log('Waiting for network to start...')
    }
    await new Promise((res)=>setTimeout(res,1000))
    }
}

networkHasStarted()
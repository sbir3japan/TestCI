# Instructions to Run the DvP Fractionalized Ownership Demo

## Prerequisites

### `jq` Command-Line JSON Processor

See https://github.com/jqlang/jq

### Node JS

https://nodejs.org/en (tested with 20.8.0)

### npx

npm install -g npx

### Yarn

https://github.com/yarnpkg/yarn

### Docker

https://www.docker.com/get-started/

### Java

Java version 17

## Running the demo application

The following steps are expected to be executed in the presented order. Each step assumes the previous executed and completed successfully.

### Clone the repositories

```
mkdir corda5-evmdemo
cd corda5-evmdemo
git clone https://github.com/corda/corda-runtime-os.git
git clone https://github.com/corda/corda-cli-plugin-host.git
```

### Build the corda-cli-plugin-host

```
cd corda-cli-plugin-host
./gradlew build
rm -rf build/plugins/*
```
```
cd ../corda-runtime-os
./gradlew :tools:plugins:package:clean :tools:plugins:package:build 
cp tools/plugins/package/build/libs/*.jar ../corda-cli-plugin-host/build/plugins/
```

### Build runtime os jar and cpbs

```
./gradlew jar
```
```
./gradlew cpbs
```

### Start a Postgresql instance

```
docker run --rm -p 5432:5432 --name postgresql -e POSTGRES_DB=cordacluster -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password postgres:latest
```

### Start the EVM test network

```
cd tools/deployNetwork
docker build -t my-ganache-network .
docker run -p 8545:8545 my-ganache-network
cd ../../
```

### Deploy the EVM contracts

cd testing/evm-interop-contracts
npx hardhat run scripts/deploy.js --network besu
cd ../../

#### Copy the contract address 

When you deploy the EVM contracts, the script will output the contract deployment address.
```
Contract Address:  0x42699A7612A82f1d9C36148af9C77354759b210b
```

If you see the same address as aboev you don't need to change anything, otherwise change the address in tools/evmdemo/next.config.js to the newly displayed address.

Current next.config.js represented below.
```
/** @type {import('next').NextConfig} */
const nextConfig = {
    env: {
        FRACTIONAL_CONTRACT_ADDRESS:"0x42699a7612a82f1d9c36148af9c77354759b210b" // replace with the new address
    }
};

module.exports = nextConfig;
```

### Startup the combined worker

./gradlew :applications:workers:release:combined-worker:build

```
java -jar ./applications/workers/release/combined-worker/build/bin/corda-combined-worker-5.1.0-EVMINTEROP.0-SNAPSHOT.jar \
--instance-id=0 \
-mbus.busType=DATABASE \
-spassphrase=password \
-ssalt=salt \
-ddatabase.user=user \
-ddatabase.pass=password \
-ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster \
-ddatabase.jdbc.directory=./applications/workers/release/combined-worker/drivers \
-rtls.crt.path=./applications/workers/release/combined-worker/tls/rest/server.crt \
-rtls.key.path=./applications/workers/release/combined-worker/tls/rest/server.key \
-rtls.ca.crt.path=./applications/workers/release/combined-worker/tls/rest/ca-chain-bundle.crt \
--serviceEndpoint=endpoints.crypto=localhost:7004 \
--serviceEndpoint=endpoints.verification=localhost:7004 \
--serviceEndpoint=endpoints.uniqueness=localhost:7004 \
--serviceEndpoint=endpoints.persistence=localhost:7004 \
--serviceEndpoint=endpoints.tokenSelection=localhost:7004

```

Wait for the combined worker to complete the startup or the following step will fail.

### Run the initialization script

```
chmod 755 start.sh
./start.sh
```

or

```
sh ./start.sh
```

Wait the script to complete without errors.

### Dev environment limitations

Due to use of HTTPS endpoints, the HTTP certificates are not trusted by the system. For this reason before you run the Web application use the same browser you'll use for the web application and navigate to the following URL

https://localhost:8888/api/v1/swagger#/Flow%20Management%20API/get_flow__holdingidentityshorthash___clientrequestid_

The browser will block the navigation to the URL warning that it is insecure, this is expected. To accept the insecure certificate generated locally and continue trusting it until the Web browser is closed, proceed as follows:

#### Safari

1. Observe the warning message stating that the connection is not secure.
2. Click on the "Show Details" button.
3. In the details, you should see an option to "Visit this website." Click on it.

#### Firefox

1. Observe the warning message stating that the connection is not secure.
2. Click on the "Advanced" button.
3. Click on "Accept the Risk and Continue.

#### Chrome

1. Observe the warning message stating that the connection is not secure.
2. Click on the "Advanced".
3. Click on "Proceed to [website] (unsafe)."

#### Opera

1. Observe the warning message stating that the connection is not secure.
2. Click on "Show details."
3. Click on "Visit the site."

### Start the Web application

```
npm install
npm run dev
```

or 

```
yarn
yarn dev
```

Using the web browser navigate to https://localhost:3000.


## Stopping all services and applications

1. From the terminal where docker was started to create a Postgresql instance, press Ctrl^C to terminate it.
2. From the terminal where docker was started to create a EVM network (Ganache) instance, press Ctrl^C to terminate it.
3. From the terminal where the combined worker was started, press Ctrl^C to terminate it.
4. From the terminal where the web application was started, press Ctrl^C to terminate it.

### IMPORTANT NOTE
Whenever you make a change to the EVM demo flows (`testing/cpbs/evm-swaps`), you will have to make sure that you apply at least the first 3 steps above and then repeat the steps to run the demo application starting from [Build runtime os jar and cpbs](#build-runtime-os-jar-and-cpbs) and repeat the steps until and (optionally) including [Start the Web application](#start-the-web-application). The [Start the Web application](#start-the-web-application) step will only be necessary if you also terminated the web application (step 4 above).

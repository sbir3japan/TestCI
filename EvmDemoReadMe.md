# Instructions to Run the DvP Fractionalized Ownership Demo

## Prerequisites


### Ganache Network Setup

``` bash
docker build -t my-ganache-network ./tools/deployNetwork
docker run -p 8545:8545 my-ganache-network
```

### Startup Postgres

``` bash
docker run --rm -p 5432:5432 --name postgresql -e POSTGRES_DB=cordacluster -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password postgres:latest
```


### Startup the combined worker

Run from Intellij (for now...)

### Run the startup script

``` bash
    bash ./start.sh
```


### Deploy the smart contract
#### Using Node.js
```
 cd testing/evm-interop-contracts
 npx hardhat compile
 npx hardhat run --network besu scripts/deploy.js
```

#### Using docker

```bash
docker build -t contract-deployer ./tools/deployContract
docker run --network host contract-deployer
```

Copy the contract address:

`
#### For example: 
    Contract address:    0x5FbDB2315678afecb367f032d93F642f64180aa3
`
DO NOT USE THE ABOVE ADDRESS, IT IS JUST AN EXAMPLE



### Run the demo

```
# If necessary go back to the root directory
cd ../../
```
# Run the demo

Navigate to the tools/evmDemo directory to startup the frontend.

```  bash
    cd tools/evmDemo
```

In the repository you will notice a .env file, copy the smart contract address to the paramater FRACTIONAL_CONTRACT_ADDRESS

``` 
    FRACTIONAL_CONTRACT_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3
```


``` bash
docker build -t swapsdemofe .
docker run -p 3000:3000  swapsdemofe
```




 
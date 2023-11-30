# Instructions to Run the DvP Fractionalized Ownership Demo

## Prerequisites


### Besu Network Setup

`
besu --network=dev --miner-enabled --miner-coinbase=0xfe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins="all" --host-allowlist="*" --rpc-ws-enabled --rpc-http-enabled --data-path=/tmp/tmpDatdir
`

### Startup Postgres

`
docker run --rm -p 5432:5432 --name postgresql -e POSTGRES_DB=cordacluster -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password postgres:latest
`


### Startup the combined worker

Run from Intellij (for now...)

### Run the startup script

`
    bash ./start.sh
`


### Deploy the smart contract

`
 cd testing/evm-interop-contracts
 npx hardhat compile
 npx hardhat run --network besu scripts/deploy.js
`

Copy the contract address:

`
#### For example: 
    Contract address:    0x5FbDB2315678afecb367f032d93F642f64180aa3
`
DO NOT USE THE ABOVE ADDRESS, IT IS JUST AN EXAMPLE



### Run the demo

`
# If necessary go back to the root directory
cd ../../

# Run the demo

Navigate to the tools/evmDemo directory to startup the frontend.

`
    cd tools/evmDemo
`

In the repository you will notice a .env file, copy the smart contract address to the paramater FRACTIONAL_CONTRACT_ADDRESS

`
    FRACTIONAL_CONTRACT_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3
`


`
npm install
npm run dev

`





 
// but useful for running the script in a standalone fashion through `node <script>`.
//
// You can also run a script with `npx hardhat run <script>`. If you do that, Hardhat
// will compile your contracts, add the Hardhat Runtime Environment's members to the
// global scope, and execute the script.
const hre = require("hardhat");

async function main() {


  const lock = await hre.ethers.deployContract("FractionalOwnershipToken", []);

  await lock.waitForDeployment();

  console.log("Contract Address: ", lock.target);

  // instantiate the contract
  // mint 10000 tokens to the deployer
  const mainAddress = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"
  const tx = await lock.createToken(mainAddress, 1000000,"DemoToken");
  await tx.wait();


}

// We recommend this pattern to be able to use async/await everywhere
// and properly handle errors.
main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

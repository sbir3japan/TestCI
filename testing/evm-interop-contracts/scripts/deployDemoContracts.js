// but useful for running the script in a standalone fashion through `node <script>`.
//
// You can also run a script with `npx hardhat run <script>`. If you do that, Hardhat
// will compile your contracts, add the Hardhat Runtime Environment's members to the
// global scope, and execute the script.
const hre = require("hardhat");

async function main() {
  const swapVault = await hre.ethers.deployContract("SwapVault", []);
  await swapVault.waitForDeployment();

  const erc20 = await hre.ethers.deployContract("Token", []);
  await erc20.waitForDeployment();

  const fractionalOwnershipToken = await hre.ethers.deployContract(
    "FractionalOwnershipToken",
    []
  );
  await fractionalOwnershipToken.waitForDeployment();

  // instantiate the contract
  // mint 10000 tokens to the deployer
  const mainAddress = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
  const tx = await fractionalOwnershipToken.createToken(
    mainAddress,
    1000000,
    "DemoToken"
  );
  await tx.wait();

  const swapVaultAddress = swapVault.target;
  const erc20Address = erc20.target;
  const fractionalOwnershipTokenAddress = fractionalOwnershipToken.target;

  // return the contractAddresses to the process
  process.stdout.write(
    JSON.stringify({
      SWAP_VAULT_ADDRESS: swapVaultAddress,
      ERC20_ADDRESS: erc20Address,
      FRACTIONAL_CONTRACT_ADDRESS: fractionalOwnershipTokenAddress,
    })
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

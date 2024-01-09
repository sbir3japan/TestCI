/** @type {import('next').NextConfig} */
const nextConfig = {
  env: {
    SWAP_VAULT_ADDRESS: "0x42699A7612A82f1d9C36148af9C77354759b210b",
    ERC20_ADDRESS: "0xa50a51c09a5c451C52BB714527E1974b686D8e77",
    FRACTIONAL_CONTRACT_ADDRESS: "0x9a3DBCa554e9f6b9257aAa24010DA8377C57c17e",
    RPC_URL: "http://host.docker.internal:8545",
  },
};
module.exports = nextConfig;

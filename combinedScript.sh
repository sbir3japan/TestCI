docker run -d --rm -p 5432:5432 --name postgresql -e POSTGRES_DB=cordacluster -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password postgres:latest
sleep 10

docker build -t combinedworker .
docker run -d -p 127.0.0.1:8888:8888 combinedworker

cd network
docker build -t besu .
docker run -d -p 127.0.0.1:8545:8545 besu
cd ..


sleep 20
bash ignace.sh

cd testing/evm-interop-contracts

docker build -t my_hardhat_image .
SMART_CONTRACT_ADDRESSES=$(docker run my_hardhat_image)
echo $SMART_CONTRACT_ADDRESSES


cd ../../tools/evmdemo

cat <<EOF > next.config.js
/** @type {import('next').NextConfig} */
const nextConfig = {
    env: $SMART_CONTRACT_ADDRESSES
};
module.exports = nextConfig;
EOF



docker build -t evmdemo .
docker run -d -p 3000:3000 evmdemo

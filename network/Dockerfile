# Use Ubuntu as the base image
FROM ubuntu:latest

# Install dependencies
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk


WORKDIR /app

RUN apt-get update \
    && apt-get install -y wget unzip \
    && rm -rf /var/lib/apt/lists/*

RUN wget https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/23.4.1/besu-23.4.1.zip \
    && unzip besu-23.4.1.zip \
    && rm besu-23.4.1.zip

# Set the working directory
WORKDIR /app/besu-23.4.1

# Expose ports
EXPOSE 8545

# Run Besu with the specified CMD
CMD ["./bin/besu", "--network=dev", "--miner-enabled", "--miner-coinbase=0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", "--rpc-http-cors-origins=all", "--host-allowlist=*", "--rpc-ws-enabled", "--rpc-http-enabled", "--data-path=/tmp/tmpDatdir","--rpc-http-host=0.0.0.0"]

## Use an official Besu image as a base
#FROM
#
## Copy the custom configuration file
#COPY besu.conf /etc/besu/besu.conf
#
## Start Besu with the specified command
#CMD ["besu"]
#
# besu --network=dev --miner-enabled --miner-coinbase=0xfe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins="all" --host-allowlist="*" --rpc-ws-enabled --rpc-http-enabled --data-path=/tmp/tmpDatdir
#
##docker run -p 30303:30303 -p 8545:8545 -e BESU_RPC_HTTP_ENABLED="true" -e BESU_NETWORK="dev" -e BESU_MINER_ENABLED="true" -e BESU_MINER_COINBASE="0xfe3b557e8fb62b89f4916b721be55ceb828dbd73" -e BESU_RPC_HTTP_CORS_ORIGINS="all" -e BESU_RPC_HTTP_ENABLED="true" -e BESU_HOST_ALLOWLIST="*" hyperledger/besu:latest
# Use Ubuntu as the base image
FROM ubuntu:latest

# Install dependencies
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk

# Set the working directory
WORKDIR /app

# Copy the local directory to the container
COPY . /app

# Expose the port on which your application will run
EXPOSE 8888

ENV CORDA_ARTIFACTORY_USERNAME=ignace.loomans@r3.com
ENV CORDA_ARTIFACTORY_PASSWORD=REPLACEME



# Define the command to run your application
#CMD ["./gradlew", ":applications:workers:release:combined-worker:build"]

CMD ["java", "-jar", "./applications/workers/release/combined-worker/build/bin/corda-combined-worker-5.1.0-EVMINTEROP.0-SNAPSHOT.jar", \
    "--instance-id=0", \
    "-mbus.busType=DATABASE", \
    "-spassphrase=password", \
    "-ssalt=salt", \
    "-ddatabase.user=user", \
    "-ddatabase.pass=password", \
    "-ddatabase.jdbc.url=jdbc:postgresql://host.docker.internal:5432/cordacluster", \
    "-ddatabase.jdbc.directory=./applications/workers/release/combined-worker/drivers", \
    "-rtls.crt.path=./applications/workers/release/combined-worker/tls/rest/server.crt", \
    "-rtls.key.path=./applications/workers/release/combined-worker/tls/rest/server.key", \
    "-rtls.ca.crt.path=./applications/workers/release/combined-worker/tls/rest/ca-chain-bundle.crt", \
    "--serviceEndpoint=endpoints.crypto=localhost:7004", \
    "--serviceEndpoint=endpoints.verification=localhost:7004", \
    "--serviceEndpoint=endpoints.uniqueness=localhost:7004", \
    "--serviceEndpoint=endpoints.persistence=localhost:7004", \
    "--serviceEndpoint=endpoints.tokenSelection=localhost:7004"]

# # use Ubuntu
# FROM ubuntu:latest
#
#
# # FROM node:18-alpine
# # # use java 17
# # FROM openjdk:17
# #
# # # make sure postgres is installed
# # # from postgres:latest
# #
# #
# # # make sure java is installed
# # RUN java -version
#
# # install nodejs
# RUN apt-get update
#
#
# # install java
# RUN apt-get install -y openjdk-17-jdk
#
#
#
# # copy the local directory
# COPY . /app
#
# # set the working directory
# WORKDIR /app
#
# EXPOSE 8888
#
#
# CMD ["java -jar ./applications/workers/release/combined-worker/build/bin/corda-combined-worker-5.1.0-EVMINTEROP.0-SNAPSHOT.jar \
#     --instance-id=0 \
#     -mbus.busType=DATABASE \
#     -spassphrase=password \
#     -ssalt=salt \
#     -ddatabase.user=user \
#     -ddatabase.pass=password \
#     -ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster \
#     -ddatabase.jdbc.directory=./applications/workers/release/combined-worker/drivers \
#     -rtls.crt.path=./applications/workers/release/combined-worker/tls/rest/server.crt \
#     -rtls.key.path=./applications/workers/release/combined-worker/tls/rest/server.key \
#     -rtls.ca.crt.path=./applications/workers/release/combined-worker/tls/rest/ca-chain-bundle.crt \
#     --serviceEndpoint=endpoints.crypto=localhost:7004 \
#     --serviceEndpoint=endpoints.verification=localhost:7004 \
#     --serviceEndpoint=endpoints.uniqueness=localhost:7004 \
#     --serviceEndpoint=endpoints.persistence=localhost:7004 \
#     --serviceEndpoint=endpoints.tokenSelection=localhost:7004"]
#
#
#
#
#
# #
# # # navigate to tools directory
# # # RUN cd ./tools
# # # WORKDIR /app/tools/networkStartupHelper
# #
# #
# # run npm i --prefix ./tools/networkStartupHelper
# # run  node ./tools/networkStartupHelper/index
# # run ./script.sh
# #
# #
# #
# #
# #
# # RUN npm install -g ganache-cli
# #
# # # Expose the port that ganache-cli will use (default is 8545)
# # EXPOSE 8545
# #
# # # Set the entry point to run ganache-cli
# # run ganache-cli --deterministic --host 0.0.0.0 --port 8545 --account 0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63,1000000000000000000000000000000000 &
# #
# #
# #
#
#
#
#
#
#
#

FROM ubuntu:latest


WORKDIR /app

COPY . .

RUN apt-get update

RUN apt-get install -y nodejs

RUN apt-get install -y npm


EXPOSE 8545

RUN npm install --prefix ./deployContract


RUN npm install -g ganache-cli
RUN ganache-cli --deterministic  --host  0.0.0.0  --port  8545  --account  0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63,1000000000000000000000000000000000

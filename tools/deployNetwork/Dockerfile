# Use an official Node.js runtime as the base image
FROM node:18

# Install ganache-cli globally
RUN npm install -g ganache-cli

# Expose the port that ganache-cli will use (default is 8545)
EXPOSE 8545

# Set the entry point to run ganache-cli
CMD ["ganache-cli","--deterministic", "--host", "0.0.0.0", "--port", "8545", "--account", "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63,1000000000000000000000000000000000"]

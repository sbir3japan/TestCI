FROM node:18-alpine

# Set the working directory
WORKDIR /app

# Copy package.json and package-lock.json to the container
COPY package*.json ./

# Install project dependencies
RUN npm install

# Copy the entire project to the container
COPY . .

# Expose any necessary ports (if required by the application)
# EXPOSE 8080

RUN npx hardhat compile

# Define the command to run when the container starts
CMD ["npx", "hardhat", "run", "scripts/deployDemoContracts.js", "--network", "dockerbesu"]

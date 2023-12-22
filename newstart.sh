#!/bin/bash

# Constants
KEYSTORE_FILE="signingkeys.pfx"
STOREPASS="DemoPassword123"
CERTIFICATE_URL="https://localhost:8888/api/v1/certificates/cluster/code-signer"
CPI_URL="https://localhost:8888/api/v1/cpi/"
VIRTUAL_NODE_URL="https://localhost:8888/api/v1/virtualnode"
MEMBERSHIP_URL="https://localhost:8888/api/v1/membership/"
FLOW_URL="https://localhost:8888/api/v1/flow/"
ADMIN_CREDENTIALS="admin:admin"
CONTENT_TYPE_JSON='Content-Type: application/json'

# Cleanup
rm -rf output.cpi

# Build operations
./gradlew cpb jar

# Function to create CPI
create_cpi() {
    local cpb_file=$1
    local cpi_file=$2
    ./../corda-cli-plugin-host/build/generatedScripts/corda-cli.sh package create-cpi \
        --cpb $cpb_file \
        --group-policy TestGroupPolicy.json \
        --cpi-name "cordapp cpi" \
        --cpi-version "1.0.0.0-SNAPSHOT" \
        --file $cpi_file \
        --keystore $KEYSTORE_FILE \
        --storepass $STOREPASS \
        --key "signing key 1"
}

# Function to upload certificates
upload_certificate() {
    local alias=$1
    local pem_file=$2

    curl --insecure -u $ADMIN_CREDENTIALS -X PUT -F alias="$alias" -F certificate=@$pem_file $CERTIFICATE_URL
    sleep 1
}

# Function to handle CPI operations
handle_cpi() {
    local cpi_path=$1
    local x500=$2

    sleep 1
    read REQUEST_ID <<< $(curl --insecure -u $ADMIN_CREDENTIALS -s -F upload=@$cpi_path $CPI_URL | jq -r '.id')
    echo "CPI Path: $cpi_path, X500: $x500, RequestID = $REQUEST_ID"

    sleep 25
    read STATUS CPI_HASH <<< $(curl --insecure -u $ADMIN_CREDENTIALS -s $CPI_URL/status/$REQUEST_ID | jq -r '.status, .cpiFileChecksum')
    printf "\nCPI Path: $cpi_path, X500: $x500, Request id = $REQUEST_ID, CPI hash = $CPI_HASH, Status = $STATUS\n\n"

    sleep 1
    HOLDING_ID=$(curl --insecure -u $ADMIN_CREDENTIALS -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$x500"'"  } }' $VIRTUAL_NODE_URL | jq -r '.requestId')
    echo "CPI Path: $cpi_path, X500: $x500, Holding ID = $HOLDING_ID"
    echo $HOLDING_ID
}


# Create CPI files
#create_cpi "testing/cpbs/evm-interop/build/libs/evm-interop-5.1.0-EVMINTEROP.0-SNAPSHOT-package.cpb" "evm-interop.cpi"
create_cpi "testing/cpbs/evm-swaps/build/libs/evm-swaps-5.1.0-EVMINTEROP.0-SNAPSHOT-package.cpb" "evm-swaps.cpi"

# Upload certificates
upload_certificate "digicert-ca" "digicert-ca.pem"
keytool -exportcert -rfc -alias "signing key 1" -keystore $KEYSTORE_FILE -storepass $STOREPASS -file signingkey1.pem
upload_certificate "signingkey1-2022" "signingkey1.pem"
upload_certificate "gradle-plugin-default-key" "gradle-plugin-default-key.pem"

# Handle CPI for Notary
NOTARY_HOLDING_ID=$(handle_cpi "./notary.cpi" "C=GB, L=London, O=Notary")

# Handle CPI for Node1
NODE1_HOLDING_ID=$(handle_cpi "./output.cpi" "CN=Testing, OU=Application, O=R3, L=London, C=GB")

# Handle CPI for Node2
NODE2_HOLDING_ID=$(handle_cpi "./output.cpi" "CN=EVM, OU=Application, O=Ethereum, L=Brussels, C=BE")

# Registration contexts
NOTARY_REGISTRATION_CONTEXT='{
  "corda.key.scheme": "CORDA.ECDSA.SECP256R1",
  "corda.roles.0": "notary",
  "corda.notary.service.name": "C=CN, L=Beijing, O=Notary",
  "corda.notary.service.flow.protocol.name": "com.r3.corda.notary.plugin.nonvalidating",
  "corda.notary.service.flow.protocol.version.0": "1"
}'
MEMBER_REGISTRATION_CONTEXT='{
   "corda.key.scheme" : "CORDA.ECDSA.SECP256R1"
}'

# Function for member registration
register_member() {
    local holding_id=$1
    local registration_request='{"memberRegistrationRequest":{"context": '$MEMBER_REGISTRATION_CONTEXT'}}'
    echo $(curl --insecure -u $ADMIN_CREDENTIALS -d "$registration_request" $MEMBERSHIP_URL$holding_id)
    sleep 1
}

# Register members and notary
register_member $NODE1_HOLDING_ID
register_member $NODE2_HOLDING_ID

NOTARY_REGISTRATION_REQUEST='{"memberRegistrationRequest":{"context": '$NOTARY_REGISTRATION_CONTEXT'}}'
echo $(curl --insecure -u $ADMIN_CREDENTIALS -d "$NOTARY_REGISTRATION_REQUEST" $MEMBERSHIP_URL$NOTARY_HOLDING_ID)

sleep 30

# Start flow for Node1
curl --insecure -u $ADMIN_CREDENTIALS \
  -X POST $FLOW_URL$NODE1_HOLDING_ID \
  -H 'accept: application/json' \
  -H $CONTENT_TYPE_JSON \
  -d '{
    "startFlow": {
        "clientRequestId": "r5",
        "flowClassName": "com.r3.corda.demo.interop.evm.IssueCurrency",
        "requestBody": {
            "symbol": "AED",
            "amount": 10000
        }
    }
}'



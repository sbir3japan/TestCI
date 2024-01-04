
rm -rf output.cpi
./gradlew cpb && ./gradlew jar


# MEMBER NODE 1 SETUP
./../corda-cli-plugin-host/build/generatedScripts/corda-cli.sh package create-cpi \
    --cpb testing/cpbs/evm-swaps/build/libs/evm-swaps-5.1.0-EVMINTEROP.0-SNAPSHOT-package.cpb  \
    --group-policy TestGroupPolicy.json \
    --cpi-name "cordapp cpi" \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file output.cpi \
    --keystore signingkey1.pfx \
    --storepass "DemoPassword123" \
    --key "signing key 1"

curl --insecure -u admin:admin -X PUT -F alias="digicert-ca1" -F certificate=@digicert-ca.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
sleep 1
keytool -exportcert -rfc -alias "signing key 1" -keystore signingkey1.pfx -storepass "DemoPassword123" -file signingkey1.pem
sleep 1
curl --insecure -u admin:admin -X PUT -F alias="signingkey1-2022" -F certificate=@signingkey1.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
sleep 1
curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
CPI=./output.cpi
sleep 1
read REQUEST_ID <<< $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id')
echo "RequestID = "$REQUEST_ID
sleep 25

read STATUS CPI_HASH < <(echo $(curl --insecure -u admin:admin -s https://localhost:8888/api/v1/cpi/status/$REQUEST_ID | jq -r '.status, .cpiFileChecksum'))
printf "\nRequest id = $REQUEST_ID   CPI hash = $CPI_HASH   Status = $STATUS\n\n"
sleep 1

### Member Node 1 Setup

read REQUEST_ID <<< $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id')
echo "RequestID = "$REQUEST_ID
sleep 5

X500="CN=Testing, OU=Application, O=R3, L=London, C=GB"
HOLDING_ID=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId')
echo "Holding ID = "$HOLDING_ID


### Member Node 2 Setup

read REQUEST_ID <<< $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id')
echo "RequestID = "$REQUEST_ID
sleep 5

X500="CN=EVM, OU=Application, O=Ethereum, L=Brussels, C=BE"
HOLDING_ID_2=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId')
echo "Holding ID 2 = "$HOLDING_ID_2

### Member Node 3 Setup

read REQUEST_ID <<< $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id')
echo "RequestID = "$REQUEST_ID
sleep 5

X500="CN=Charlie, OU=Application, O=NordVPN, L=Vilnius, C=LT"
HOLDING_ID_3=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId')
echo "Holding ID 3 = "$HOLDING_ID_3

### Member Node 4 Setup

read REQUEST_ID <<< $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id')
echo "RequestID = "$REQUEST_ID
sleep 5

X500="CN=Eve, OU=Application, O=NordVPN, L=Athens, C=GR"
HOLDING_ID_4=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId')
echo "Holding ID 4 = "$HOLDING_ID_4

#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="digicert-ca" -F certificate=@digicert-ca.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#keytool -exportcert -rfc -alias "signing key 1" -keystore signingkeys.pfx -storepass "DemoPassword123" -file signingkey1.pem
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="signingkey1-2022" -F certificate=@signingkey1.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#
#

######################################
######################################

# NOTARY SETUP

rm -rf notary.cpi

./../corda-cli-plugin-host/build/generatedScripts/corda-cli.sh package create-cpi \
    --cpb notary-plugins/notary-plugin-non-validating/notary-plugin-non-validating-server/build/libs/notary-plugin-non-validating-server-5.1.0-EVMINTEROP.0-SNAPSHOT-package.cpb \
    --group-policy TestGroupPolicy.json \
    --cpi-name "cpi name" \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file notary.cpi \
    --keystore signingkey1.pfx \
    --storepass "DemoPassword123" \
    --key "signing key 1"


CPI=./notary.cpi

sleep 1
read REQUEST_ID <<< $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id')
echo "RequestID = "$REQUEST_ID

sleep 25

curl --insecure -u admin:admin -s https://localhost:8888/api/v1/cpi/status/$REQUEST_ID
sleep 10
read STATUS CPI_HASH < <(echo $(curl --insecure -u admin:admin -s https://localhost:8888/api/v1/cpi/status/$REQUEST_ID | jq -r '.status, .cpiFileChecksum'))

printf "\nRequest id = $REQUEST_ID   CPI hash = $CPI_HASH   Status = $STATUS\n\n"
sleep 1
X500="C=GB, L=London, O=Notary"
NOTARY_HOLDING_ID=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.requestId')
echo "NOTARY HOLDING ID = "$NOTARY_HOLDING_ID



#####################
#####################




export NOTARY_REGISTRATION_CONTEXT='{
  "corda.key.scheme": "CORDA.ECDSA.SECP256R1",
  "corda.roles.0": "notary",
  "corda.notary.service.name": "C=CN, L=Beijing, O=Notary",
  "corda.notary.service.flow.protocol.name": "com.r3.corda.notary.plugin.nonvalidating",
  "corda.notary.service.flow.protocol.version.0": "1"
}'

export MEMBER_REGISTRATION_CONTEXT='{
   "corda.key.scheme" : "CORDA.ECDSA.SECP256R1"
}'

export REGISTRATION_REQUEST='{"memberRegistrationRequest":{"context": '$MEMBER_REGISTRATION_CONTEXT'}}'


echo $(curl --insecure -u admin:admin -d "$REGISTRATION_REQUEST" https://localhost:8888/api/v1/membership/$HOLDING_ID)
sleep 1

echo $(curl --insecure -u admin:admin -d "$REGISTRATION_REQUEST" https://localhost:8888/api/v1/membership/$HOLDING_ID_2)
sleep 1

echo $(curl --insecure -u admin:admin -d "$REGISTRATION_REQUEST" https://localhost:8888/api/v1/membership/$HOLDING_ID_3)
sleep 1

echo $(curl --insecure -u admin:admin -d "$REGISTRATION_REQUEST" https://localhost:8888/api/v1/membership/$HOLDING_ID_4)
sleep 1

export NOTARY_REGISTRATION_REQUEST='{"memberRegistrationRequest":{"context": '$NOTARY_REGISTRATION_CONTEXT'}}'
echo $(curl --insecure -u admin:admin -d "$NOTARY_REGISTRATION_REQUEST" https://localhost:8888/api/v1/membership/$NOTARY_HOLDING_ID)

sleep 30



#curl --insecure -u admin:admin \
#  -X POST \
#  https://localhost:8888/api/v1/flow/$HOLDING_ID \
#  -H 'accept: application/json' \
#  -H 'Content-Type: application/json' \
#  -d '{
#    "startFlow": {
#        "clientRequestId": "r5",
#        "flowClassName": "com.r3.corda.demo.interop.evm.IssueCurrency",
#        "requestBody": {
#            "symbol": "AED",
#            "amount": 10000
#        }
#    }
#}'



### OLD STUFF

#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="digicert-ca" -F certificate=@digicert-ca.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#keytool -exportcert -rfc -alias "signing key 1" -keystore signingkeys.pfx -storepass "DemoPassword123" -file signingkeys.pem
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="signingkeys-2022" -F certificate=@signingkeys.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#
#
#######################################
#######################################
## MEMBER NODE 2 SETUP
#
#rm -rf output.cpi
#
#./../corda-cli-plugin-host/build/generatedScripts/corda-cli.sh package create-cpi \
#    --cpb testing/cpbs/evm-swaps/build/libs/evm-swaps-5.1.0-EVMINTEROP.0-SNAPSHOT-package.cpb  \
#    --group-policy TestGroupPolicy.json \
#    --cpi-name "cordapp cpi" \
#    --cpi-version "1.0.0.0-SNAPSHOT" \
#    --file output.cpi \
#    --keystore signingkey2.pfx \
#    --storepass "DemoPassword123" \
#    --key "signing key 2"
#
#
#sleep 1
#CPI=./output.cpi
#
#sleep 1
#read REQUEST_ID <<< $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id')
#echo "RequestID = "$REQUEST_ID
#
#sleep 25
#read STATUS CPI_HASH < <(echo $(curl --insecure -u admin:admin -s https://localhost:8888/api/v1/cpi/status/$REQUEST_ID | jq -r '.status, .cpiFileChecksum'))
#printf "\nRequest id = $REQUEST_ID   CPI hash = $CPI_HASH   Status = $STATUS\n\n"
#sleep 1
#
#
#
#
#
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="digicert-ca" -F certificate=@digicert-ca.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#keytool -exportcert -rfc -alias "signing key 1" -keystore signingkey2.pfx -storepass "DemoPassword123" -file signingkey2.pem
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="signingkey2" -F certificate=@signingkey2.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#
#sleep 1
#CPI=./output.cpi
#
#sleep 1
#
#sleep 1
#
#
#sleep 10
#
## Register VNode 3
#
#
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="digicert-ca" -F certificate=@digicert-ca.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#keytool -exportcert -rfc -alias "signing key 1" -keystore signingkey3.pfx -storepass "DemoPassword123" -file signingkey3.pem
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="signingkey3-2022" -F certificate=@signingkey3.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#
#sleep 1
#
#
## Register VNode 4
#
#CPI=./output.cpi
#
#sleep 1
#
#
#sleep 1
#sleep 10
#
#
#
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="digicert-ca" -F certificate=@digicert-ca.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#keytool -exportcert -rfc -alias "signing key 1" -keystore signingkey4.pfx -storepass "DemoPassword123" -file signingkey4.pem
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="signingkey4-2022" -F certificate=@signingkey4.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#sleep 1
#curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
#
#sleep 1
#CPI=./output.cpi
#
#sleep 1



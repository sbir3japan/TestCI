curl --insecure -u admin:admin -X PUT -F alias="digicert-ca" -F certificate=@digicert-ca.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
sleep 1
keytool -exportcert -rfc -alias "signing key 1" -keystore signingkeys.pfx -storepass "DemoPassword123" -file signingkey1.pem
sleep 1
curl --insecure -u admin:admin -X PUT -F alias="signingkey1-2022" -F certificate=@signingkey1.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
sleep 1
curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer

sleep 1
CPI=./output.cpi

sleep 1
read REQUEST_ID < <(echo $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id'))
echo "RequestID = "$REQUEST_ID

sleep 8
read STATUS CPI_HASH < <(echo $(curl --insecure -u admin:admin -s https://localhost:8888/api/v1/cpi/status/$REQUEST_ID | jq -r '.status, .cpiFileChecksum'))
printf "\nRequest id = $REQUEST_ID   CPI hash = $CPI_HASH   Status = $STATUS\n\n"
sleep 1
X500="CN=Testing, OU=Application, O=R3, L=London, C=GB"
HOLDING_ID=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.holdingIdHash')
echo "Holding ID = "$HOLDING_ID

sleep 1

#!/bin/bash

## This is a sample script to create Server Certificate file.
##
## install cfssl like 'apt-get install golang-cfssl'

CA_FILE=./my-ca.pem
CA_KEY=./my-ca-key.pem

cat <<EOF > config.json
{
  "signing": {
    "default": {
      "expiry": "87600h",
      "usages": ["signing", "key encipherment", "server auth", "client auth"]
    }
  }
}
EOF

cat <<EOF > server.json
{
    "CN": "api.example-tls.tk",
    "hosts": [
        "localhost",
        "127.0.0.1"
    ],
    "key": {
        "algo": "ecdsa",
        "size": 256
    },
    "names": [
        {
            "OU": "",
            "O": "",
            "C": "",
            "ST": "",
            "L": ""
        }
    ]
}
EOF

cfssl gencert -ca=$CA_FILE -ca-key=$CA_KEY \
     -config=config.json  server.json | cfssljson -bare web-api-server

# create PKCS#12 server cert with no password
openssl pkcs12 -export -in web-api-server.pem -inkey web-api-server-key.pem -out server-cert.p12 -passout pass:

# remove temporary files. (JSON, CSR)
rm *.json *.csr

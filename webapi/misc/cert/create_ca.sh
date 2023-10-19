#!/bin/bash

## This is a sample script to create CA Certificate file.
##
## install cfssl like 'apt-get install golang-cfssl'

cat <<EOF > ca_csr.json
{
    "CN": "example-tsl.tk",
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

cfssl gencert -initca ca_csr.json | cfssljson -bare my-ca

# remove temporary files. (JSON, CSR)
rm *.json *.csr

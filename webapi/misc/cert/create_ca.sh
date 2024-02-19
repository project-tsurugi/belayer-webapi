#!/bin/bash -eu

## This is a sample script to create CA Certificate file.
##
## install cfssl like 'apt-get install golang-cfssl'

cd $(dirname $0)

set +e
which cfssl >/dev/null 2>&1
if [ "$?" -ne "0" ];then
  printf "%s\n" "cfssl is not available. install cfssl."
  exit 1
fi
set -e

cat <<EOF > ca_csr.json
{
    "CN": "example-tls.tk",
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

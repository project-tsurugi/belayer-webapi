#!/bin/bash

TOKEN=`curl -s -d uid=user1 -d pw=pw1 localhost:8000/api/auth | jq -r ".accessToken"`

BODY=$(cat <<EOF
{
  "files": [
    "load1/dumpFile1.parquet"
  ]
}
EOF
)

JOB_ID=`curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type:application/json" localhost:8000/api/load/demo -d "$BODY" | jq -r ".jobId"`

echo $JOB_ID

echo list
curl -s -H "Authorization: Bearer $TOKEN" localhost:8000/api/dumpload/list/load | jq

echo status
curl -s -H "Authorization: Bearer $TOKEN" localhost:8000/api/dumpload/status/load/$JOB_ID | jq


echo "wait for job finishing...."
sleep 12

curl -s -H "Authorization: Bearer $TOKEN" localhost:8000/api/dumpload/status/load/$JOB_ID | jq


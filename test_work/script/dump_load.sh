#!/bin/bash

TOKEN=`curl -s -d uid=tsurugi -d pw=password localhost:8000/api/auth | jq -r ".accessToken"`

FILE=`curl -s -X POST -H "Authorization: Bearer $TOKEN" "localhost:8000/api/dump/dump1/demo?wait_until_done=true" | jq -r ".files[0]"`

BODY=$(cat <<EOF
{
  "files": [
    "${FILE}"
  ]
}
EOF
)

JOB_ID=`curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type:application/json" "localhost:8000/api/load/demo?wait_until_done=true" -d "$BODY" | jq -r ".jobId"`

echo $JOB_ID

#echo list
#curl -s -H "Authorization: Bearer $TOKEN" localhost:8000/api/dumpload/list/load | jq

echo status
curl -s -H "Authorization: Bearer $TOKEN" localhost:8000/api/dumpload/status/load/$JOB_ID | jq


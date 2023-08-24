#!/bin/bash

echo "start transaction"
JOB_ID=`curl -s -X POST -H "Authorization:Bearer aaa:111" localhost:8000/api/transaction/begin/read_write/10 | jq -r '.transactionId'`

echo "status"
curl -s -H "Authorization: Bearer aaa:111" localhost:8000/api/transaction/status/$JOB_ID | jq

echo "dump"
#curl -s -X POST -H "Authorization:Bearer aaa:111" "localhost:8000/api/transaction/dump/${JOB_ID}/FOO_TBL" | jq
curl -s -X POST -H "Authorization:Bearer aaa:111" "localhost:8000/api/transaction/dump/${JOB_ID}/FOO_TBL?mode=sse"

echo "status"
curl -s -H "Authorization: Bearer aaa:111" localhost:8000/api/transaction/status/$JOB_ID | jq

echo "load"
curl -s -X POST -H "Authorization:Bearer aaa:111" localhost:8000/api/transaction/load/${JOB_ID}/FOO_TBL -F file=@d1.parquet -F col-map=col1,cola | jq

echo "status"
curl -s -H "Authorization: Bearer aaa:111" localhost:8000/api/transaction/status/$JOB_ID | jq

echo "commit"
curl -s -X POST -H "Authorization:Bearer aaa:111" localhost:8000/api/transaction/commit/${JOB_ID} | jq

echo "status"
curl -s -H "Authorization: Bearer aaa:111" localhost:8000/api/transaction/status/$JOB_ID | jq

echo "done"

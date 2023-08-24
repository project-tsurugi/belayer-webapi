#!/bin/bash

JOB_ID=`curl -s -X POST -H "Authorization: Bearer aaa:111" localhost:8000/api/backup/bk1 | jq -r ".jobId"`
curl -s -H "Authorization: Bearer aaa:111" localhost:8000/api/br/status/backup/$JOB_ID | jq

echo "wait for job finishing...."
sleep 12

curl -s -H "Authorization: Bearer aaa:111" localhost:8000/api/br/status/backup/$JOB_ID | jq

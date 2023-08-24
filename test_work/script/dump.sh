#!/bin/bash

TOKEN=`curl -s -d uid=user1 -d pw=pw1 localhost:8000/api/auth | jq -r ".accessToken"`

#JOB_ID=`curl -s -X POST -H "Authorization: Bearer $TOKEN" "localhost:8000/api/dump/dump1/demo?wait_until_done=true" | jq -r ".jobId"`
JOB_ID=`curl -s -X POST -H "Authorization: Bearer $TOKEN" "localhost:8000/api/dump/dump1/demo" | jq -r ".jobId"`
curl -s -H "Authorization: Bearer $TOKEN" localhost:8000/api/dumpload/status/dump/$JOB_ID | jq


echo "wait for job finishing...."
sleep 13

curl -s -H "Authorization: Bearer $TOKEN" localhost:8000/api/dumpload/status/dump/$JOB_ID | jq

#!/bin/bash

echo "upload"
curl -s -X POST -H "Authorization:Bearer aaa:111" localhost:8000/api/upload/upld -F file=@d1.parquet | jq

echo "dirlist"
curl -s -X GET -H "Authorization:Bearer aaa:111" "localhost:8000/api/dirlist/upld?hide_dir=true" | jq

FILE="test.parquet"

echo "download"
curl -s -H "Authorization:Bearer aaa:111" localhost:8000/api/download/upld%2Fd1.parquet --output $FILE

echo "local file check"

if [ -e $FILE ]; then
  echo "OK: File exists. file=$FILE"
else
  echo "NG: File exists. file=$FILE"
fi

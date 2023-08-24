#!/bin/bash

curl -H "Authorization: Bearer aaa:123" localhost:8000/api/exec_sql -X POST -d "delete from demo"

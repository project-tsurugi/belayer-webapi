#!/bin/bash


java -Djava.io.tmpdir=`pwd`/test_work/tmp -Dspring.profiles.active=ft -jar /opt/belayer/app/belayer.jar 2>&1 &

/usr/local/bin/docker-entrypoint.sh

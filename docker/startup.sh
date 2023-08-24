#!/bin/bash

java -jar ${BELAYER_HOME}/app/belayer.jar 2>&1 &

/usr/local/bin/docker-entrypoint.sh

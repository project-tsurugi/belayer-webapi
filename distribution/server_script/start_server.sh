#!/bin/bash

cd `dirname $0`
PARENT_DIR=$(cd ..;pwd)
JAR_NAME=tsurugi-belayer.jar

if [ -z $TSURUGI_HOME ]; then
    echo ""
    echo "TSURUGI_HOME is set to the empty string"
    exit -1
fi

JAR=`ls ${PARENT_DIR}/jar/${JAR_NAME}`

# specify environment variables and parameters in config/tsurugi-belayer.conf
CONF_FOLDER=${PARENT_DIR}/config

# execute full executable jar
${JAR}


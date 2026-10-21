#!/bin/bash

cd `dirname $0`
PARENT_DIR=$(cd ..;pwd)
JAR_NAME=tsurugi-belayer.jar

# specify environment variables and parameters in config/tsurugi-belayer.conf
export CONF_FOLDER=${PARENT_DIR}/config

# The launch script in the JAR sources the conf without exporting it, so
# only JAVA_OPTS and RUN_ARGS would take effect. Export everything in the
# conf here, the same as systemd does with EnvironmentFile.
if [ -f ${CONF_FOLDER}/tsurugi-belayer.conf ]; then
    set -a
    . ${CONF_FOLDER}/tsurugi-belayer.conf
    set +a
fi

if [ -z $TSURUGI_HOME ]; then
    echo ""
    echo "TSURUGI_HOME is set to the empty string"
    exit -1
fi

JAR=`ls ${PARENT_DIR}/jar/${JAR_NAME}`

# execute full executable jar
${JAR}


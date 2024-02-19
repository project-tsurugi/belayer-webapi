#!/bin/bash

cd `dirname $0`
PARENT_DIR=$(cd ..;pwd)
JAR_NAME_PREFIX=tsurugi-webapp-

if [ -z $TSURUGI_HOME ]; then
    echo ""
    echo "TSURUGI_HOME is set to the empty string"
    exit -1
fi

_JAVA_PATH=$(which java)
if [ -z $_JAVA_PATH ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ];  then
    _JAVA_PATH="$JAVA_HOME/bin/java"
fi

JAR=`ls ${PARENT_DIR}/jar/${JAR_NAME_PREFIX}*.jar`


#JAVA_OPTS="${JAVA_OPTS} -Xms=512M -Xmx512M -XX:MaxMetaspaceSize=512M"
JAVA_OPTS=${JAVA_OPTS}
BELAYER_APP_OPTS=

$_JAVA_PATH ${JAVA_OPTS} -jar ${JAR} ${BELAYER_APP_OPTS}


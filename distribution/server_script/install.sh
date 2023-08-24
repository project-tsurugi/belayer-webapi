#!/bin/sh

cd `dirname $0`
VERSION=#VERSION#
REQUIRE_JAVA_MAJOR_VERSION=11
JAR_NAME_PREFIX=tsurugi-webapp-

echo "start install."

# get option
while [ $# -gt 0 ]
do
  if test "$1" != "--prefix=*"
  then
    _INSTALL_PREFIX=${1#--prefix=}
  fi
  shift
done

# default install dir
if [ "${_INSTALL_PREFIX}" = "" ]; then
    _INSTALL_PREFIX="/usr/lib"
fi
INSTALL_DIR=${_INSTALL_PREFIX}/tsurugi-webapp-$VERSION

# check java path and version
_JAVA_PATH=$(which java)
if [ -z $_JAVA_PATH ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ];  then
    _JAVA_PATH="$JAVA_HOME/bin/java"
fi

if [ !$_JAVA_PATH ]; then
    _JAVA_MAJOR_VERSION=$($_JAVA_PATH -version 2>&1 | grep -oP 'version "?(1\.)?\K\d+' || true)
    _JAVA_FULL_VERSION=$($_JAVA_PATH -version 2>&1)
fi

echo Java Version: $_JAVA_FULL_VERSION
echo Java MajorVersion: $_JAVA_MAJOR_VERSION
echo Java Path: $_JAVA_PATH

if [ $_JAVA_MAJOR_VERSION -lt "11" ]; then
    echo ""
    echo "requires Java 11 or newer."
    exit -1
fi

mkdir -p ${INSTALL_DIR}/bin/
mkdir -p ${INSTALL_DIR}/jar/

/bin/cp start_server.sh $INSTALL_DIR/bin/start_server.sh
/bin/cp app/${JAR_NAME_PREFIX}*.jar $INSTALL_DIR/jar/

chmod u+x $INSTALL_DIR/bin/start_server.sh

echo "install dir: $INSTALL_DIR"
echo "installed."

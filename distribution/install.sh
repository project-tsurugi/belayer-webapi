#!/bin/sh

cd `dirname $0`
VERSION=#VERSION#
REQUIRE_JAVA_MAJOR_VERSION=11
JAR_NAME=tsurugi-belayer

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
INSTALL_DIR=${_INSTALL_PREFIX}/tsurugi-belayer-$VERSION

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

if [ $_JAVA_MAJOR_VERSION -lt "${REQUIRE_JAVA_MAJOR_VERSION}" ]; then
    echo ""
    echo "requires Java ${REQUIRE_JAVA_MAJOR_VERSION} or newer."
    exit -1
fi

# check TSURUGI_HOME
if [ -z $TSURUGI_HOME ]; then
    echo ""
    echo "TSURUGI_HOME is not set"
    exit -1
fi

mkdir -p ${INSTALL_DIR}/bin
mkdir -p ${INSTALL_DIR}/jar
mkdir -p ${INSTALL_DIR}/config

cp bin/*.sh $INSTALL_DIR/bin/
cp app/${JAR_NAME} $INSTALL_DIR/jar/
cp config/* $INSTALL_DIR/config/

sed -i "s|##BELAYER_INSTALL_DIR##|${INSTALL_DIR}|g" $INSTALL_DIR/systemd/tsurugi-belayer.service
sed -i "s|##BELAYER_INSTALL_DIR##|${INSTALL_DIR}|g" $INSTALL_DIR/config/tsurugi-belayer.conf
sed -i "s|##TSURUGI_HOME##|${TSURUGI_HOME}|g" $INSTALL_DIR/config/tsurugi-belayer.conf

chmod u+x $INSTALL_DIR/bin/*.sh

echo "install dir: $INSTALL_DIR"
echo "installed."

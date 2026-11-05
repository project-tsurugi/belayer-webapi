#!/bin/sh

cd `dirname $0`
VERSION=#VERSION#
REQUIRE_JAVA_MAJOR_VERSION=11

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

if [ -z "$_JAVA_PATH" ]; then
    echo ""
    echo "java is not found. requires Java ${REQUIRE_JAVA_MAJOR_VERSION} or newer."
    exit 1
fi

_JAVA_MAJOR_VERSION=$($_JAVA_PATH -version 2>&1 | grep -oP 'version "?(1\.)?\K\d+' || true)
_JAVA_FULL_VERSION=$($_JAVA_PATH -version 2>&1)

echo Java Version: $_JAVA_FULL_VERSION
echo Java MajorVersion: $_JAVA_MAJOR_VERSION
echo Java Path: $_JAVA_PATH

# An empty version (java -version printed something unexpected) fails the
# check as well, instead of letting the comparison error out and go on.
if [ -z "$_JAVA_MAJOR_VERSION" ] || [ "$_JAVA_MAJOR_VERSION" -lt "${REQUIRE_JAVA_MAJOR_VERSION}" ]; then
    echo ""
    echo "requires Java ${REQUIRE_JAVA_MAJOR_VERSION} or newer."
    exit 1
fi

# check TSURUGI_HOME
if [ -z $TSURUGI_HOME ]; then
    echo ""
    echo "TSURUGI_HOME is not set"
    exit 1
fi

mkdir -p ${INSTALL_DIR}/bin
mkdir -p ${INSTALL_DIR}/jar
mkdir -p ${INSTALL_DIR}/config
mkdir -p ${INSTALL_DIR}/systemd

cp bin/*.sh $INSTALL_DIR/bin/
cp app/*.jar $INSTALL_DIR/jar/
cp config/* $INSTALL_DIR/config/
cp systemd/* $INSTALL_DIR/systemd/
cp script/* $TSURUGI_HOME/bin/
# The stubs keep the current mode in mode.txt and report "stop" without it,
# until a start through Belayer writes it. Keep a file the stubs have written.
if [ ! -e $TSURUGI_HOME/bin/mode.txt ]; then
    echo "standalone" > $TSURUGI_HOME/bin/mode.txt
fi

sed -i "s|##BELAYER_INSTALL_DIR##|${INSTALL_DIR}|g" $INSTALL_DIR/systemd/tsurugi-belayer.service
sed -i "s|##BELAYER_INSTALL_DIR##|${INSTALL_DIR}|g" $INSTALL_DIR/config/tsurugi-belayer.conf
sed -i "s|##TSURUGI_HOME##|${TSURUGI_HOME}|g" $INSTALL_DIR/config/tsurugi-belayer.conf

chmod u+x $INSTALL_DIR/jar/*.jar
chmod u+x $INSTALL_DIR/bin/*.sh
chmod u+x $INSTALL_DIR/systemd/*.sh
chmod u+x $TSURUGI_HOME/bin/*_w

echo "install dir: $INSTALL_DIR"
echo "installed."

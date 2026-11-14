#!/bin/sh
#
# The first step of the installation: installs the packages Belayer needs, as
# root. Ubuntu (apt). Then run install.sh as the user of the TsurugiDB.
#
#   Java 11 or newer   runs Belayer (openjdk-17-jre-headless when missing)
#   jq                 used to report the status of the TsurugiDB
#   python3 (3.10 -)   runs the wrappers tgctl_w and tgha_w
#   curl               the unit of the authentication server waits with it
#                      until the server answers (register_service.sh)

REQUIRE_JAVA_MAJOR_VERSION=11

if [ "$(id -u)" -ne 0 ]; then
    echo "[ERROR] run $(basename $0) as root." 1>&2
    exit 1
fi

_PACKAGES=""
_JAVA_MAJOR_VERSION=""
if command -v java > /dev/null 2>&1; then
    _JAVA_MAJOR_VERSION=$(java -version 2>&1 | grep -oP 'version "?(1\.)?\K\d+' || true)
fi
if [ -z "$_JAVA_MAJOR_VERSION" ] || [ "$_JAVA_MAJOR_VERSION" -lt "$REQUIRE_JAVA_MAJOR_VERSION" ]; then
    _PACKAGES="$_PACKAGES openjdk-17-jre-headless"
fi
command -v jq > /dev/null 2>&1 || _PACKAGES="$_PACKAGES jq"
[ -x /usr/bin/python3 ] || _PACKAGES="$_PACKAGES python3"
command -v curl > /dev/null 2>&1 || _PACKAGES="$_PACKAGES curl"

if [ -z "$_PACKAGES" ]; then
    echo "all packages are installed."
    exit 0
fi
echo "install:$_PACKAGES"
apt-get update && apt-get install -y $_PACKAGES || exit 1
echo "installed."

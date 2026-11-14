#!/bin/sh
#
# Lays out Belayer for one TsurugiDB. Run it as the OS user that runs that
# TsurugiDB (root is not needed): Belayer and TsurugiDB read and write each
# other's files, and Belayer puts its wrappers in $TSURUGI_HOME/bin.
#
# The installation has three steps; this script is the second one.
#   1. apt-install.sh (root): Java, jq and Python 3.
#   2. install.sh (the user of the TsurugiDB): lays out the files. It does not
#      register any service; bin/start_server.sh starts Belayer at once.
#   3. <install dir>/systemd/register_service.sh (root, optional): registers
#      Belayer and the TsurugiDB as systemd services.

VERSION=#VERSION#
REQUIRE_JAVA_MAJOR_VERSION=11
DEFAULT_SERVER_PORT=8000
DEFAULT_MANAGEMENT_PORT=18000

usage() {
  cat <<EOF 1>&2
Usage: $(basename $0) [OPTION]...

  --tsurugi-home=<dir>    TsurugiDB to work with. [default: \$TSURUGI_HOME]
  --tsurugi-conf=<file>   configuration file of that TsurugiDB.
                          [default: \$TSURUGI_CONF, or <tsurugi home>/var/etc/tsurugi.ini]
  --prefix=<dir>          directory to create tsurugi-belayer-${VERSION} in.
                          [default: the directory that holds the TsurugiDB]
  --storage=<dir>         storage root of Belayer (the files of the users, the
                          job history, the role assignments).
                          [default: <install dir>/storage]
  --port=<port>           port of the Web API. [default: ${DEFAULT_SERVER_PORT}]
  --management-port=<port>
                          port of the management endpoints. [default: ${DEFAULT_MANAGEMENT_PORT}]
  -h, --help              display this help and exit

Run it as the OS user that runs the TsurugiDB.
EOF
}

# make_absolute <path> : prints the path, relative ones resolved against the
# directory install.sh was started from (it changes the directory later).
make_absolute() {
  case "$1" in
    /*) echo "$1" ;;
    *) echo "$(pwd)/$1" ;;
  esac
}

# check_port <option> <value> : stops unless the value is a port number.
check_port() {
  case "$2" in
    "" | *[!0-9]*)
      echo "[ERROR] $1 must be a port number (1-65535): $2" 1>&2
      exit 1
      ;;
  esac
  if [ "$2" -lt 1 ] || [ "$2" -gt 65535 ]; then
    echo "[ERROR] $1 must be a port number (1-65535): $2" 1>&2
    exit 1
  fi
}

# ini_value <file> <section> <key> : prints the value of the key in the
# section of tsurugi.ini, or nothing. Commented lines do not count.
ini_value() {
  awk -v sec="[$2]" -v key="$3" '
    { line = $0; sub(/^[ \t]+/, "", line); sub(/[ \t\r]+$/, "", line) }
    line ~ /^\[/ { insec = (line == sec); next }
    insec && line !~ /^[#;]/ {
      n = index(line, "=")
      if (n > 0) {
        k = substr(line, 1, n - 1); sub(/[ \t]+$/, "", k)
        if (k == key) { v = substr(line, n + 1); sub(/^[ \t]+/, "", v); val = v }
      }
    }
    END { print val }' "$1"
}

# get options
_PREFIX_OPT=""
_STORAGE_OPT=""
SERVER_PORT=${DEFAULT_SERVER_PORT}
MANAGEMENT_PORT=${DEFAULT_MANAGEMENT_PORT}
while [ $# -gt 0 ]
do
  case "$1" in
    --tsurugi-home=*) TSURUGI_HOME=$(make_absolute "${1#--tsurugi-home=}") ;;
    --tsurugi-conf=*) TSURUGI_CONF=$(make_absolute "${1#--tsurugi-conf=}") ;;
    --prefix=*) _PREFIX_OPT=$(make_absolute "${1#--prefix=}") ;;
    --storage=*) _STORAGE_OPT=$(make_absolute "${1#--storage=}") ;;
    --port=*) SERVER_PORT=${1#--port=} ;;
    --management-port=*) MANAGEMENT_PORT=${1#--management-port=} ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "[ERROR] unknown option: $1" 1>&2
      usage
      exit 1
      ;;
  esac
  shift
done

cd `dirname $0`

echo "start install."

# check java path and version
_JAVA_PATH=$(which java)
if [ -z $_JAVA_PATH ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ];  then
    _JAVA_PATH="$JAVA_HOME/bin/java"
fi

if [ -z "$_JAVA_PATH" ]; then
    echo ""
    echo "java is not found. requires Java ${REQUIRE_JAVA_MAJOR_VERSION} or newer."
    echo "run apt-install.sh as root first."
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
if [ -z "$TSURUGI_HOME" ]; then
    echo ""
    echo "TSURUGI_HOME is not set. set the environment variable or use --tsurugi-home=<dir>."
    exit 1
fi
TSURUGI_HOME=${TSURUGI_HOME%/}
if [ ! -x "$TSURUGI_HOME/bin/tgctl" ]; then
    echo "[ERROR] $TSURUGI_HOME/bin/tgctl is not found: TSURUGI_HOME is not a TsurugiDB." 1>&2
    exit 1
fi
if [ ! -w "$TSURUGI_HOME/bin" ]; then
    echo "[ERROR] $(id -un) cannot write to $TSURUGI_HOME/bin." 1>&2
    echo "[ERROR] run install.sh as the user that runs the TsurugiDB ($(stat -L -c %U "$TSURUGI_HOME"))." 1>&2
    exit 1
fi
if [ "$(stat -L -c %U "$TSURUGI_HOME")" != "$(id -un)" ]; then
    echo "[WARN] $TSURUGI_HOME belongs to $(stat -L -c %U "$TSURUGI_HOME"), not to $(id -un)."
    echo "[WARN] Belayer and TsurugiDB have to run as the same user: dump, load, backup and"
    echo "[WARN] restore fail otherwise."
fi

# The configuration of the TsurugiDB, and the IPC endpoint to reach it at: a
# node with several TsurugiDBs gives each its own database_name.
if [ -z "$TSURUGI_CONF" ]; then
    TSURUGI_CONF=$TSURUGI_HOME/var/etc/tsurugi.ini
fi
if [ ! -f "$TSURUGI_CONF" ]; then
    echo "[ERROR] the configuration of TsurugiDB is not found: $TSURUGI_CONF" 1>&2
    exit 1
fi
_TG_DB_NAME=$(ini_value "$TSURUGI_CONF" ipc_endpoint database_name)
TSURUGI_URL=ipc:${_TG_DB_NAME:-tsurugi}

check_port --port "$SERVER_PORT"
check_port --management-port "$MANAGEMENT_PORT"
if [ "$SERVER_PORT" -eq "$MANAGEMENT_PORT" ]; then
    echo "[ERROR] --port and --management-port are the same: $SERVER_PORT" 1>&2
    exit 1
fi
if command -v ss > /dev/null 2>&1; then
    for _port in "$SERVER_PORT" "$MANAGEMENT_PORT"; do
        if [ -n "$(ss -Htln "sport = :$_port" 2> /dev/null)" ]; then
            echo "[WARN] port $_port is already in use (fine when it is this Belayer being re-installed)."
        fi
    done
fi

# The install directory sits beside the TsurugiDB unless told otherwise: the
# user that runs the TsurugiDB can write there, and /usr/lib/tsurugi gives
# /usr/lib as before.
if [ -n "${_PREFIX_OPT}" ]; then
    _INSTALL_PREFIX=${_PREFIX_OPT%/}
else
    _INSTALL_PREFIX=$(dirname "$TSURUGI_HOME")
fi
INSTALL_DIR=${_INSTALL_PREFIX}/tsurugi-belayer-$VERSION
STORAGE_ROOT=${_STORAGE_OPT:-$INSTALL_DIR/storage}
STORAGE_ROOT=${STORAGE_ROOT%/}

if ! mkdir -p ${INSTALL_DIR}/bin ${INSTALL_DIR}/jar ${INSTALL_DIR}/config ${INSTALL_DIR}/systemd ${INSTALL_DIR}/proc; then
    echo "[ERROR] cannot create ${INSTALL_DIR}." 1>&2
    exit 1
fi
if ! mkdir -p "$STORAGE_ROOT"; then
    echo "[ERROR] cannot create the storage root $STORAGE_ROOT." 1>&2
    exit 1
fi

cp bin/*.sh $INSTALL_DIR/bin/
cp app/*.jar $INSTALL_DIR/jar/
cp systemd/* $INSTALL_DIR/systemd/
cp script/* $TSURUGI_HOME/bin/
# The stubs keep the current mode in mode.txt and report "stop" without it,
# until a start through Belayer writes it. Keep a file the stubs have written.
if [ ! -e $TSURUGI_HOME/bin/mode.txt ]; then
    echo "standalone" > $TSURUGI_HOME/bin/mode.txt
fi
if [ -d webadmin ]; then
    cp -R webadmin $INSTALL_DIR/
fi

# A re-install keeps the settings already there, so that nothing the operator
# has changed (or register_service.sh has written) is lost.
_KEPT_CONF=""
for _f in config/*; do
    _name=$(basename "$_f")
    if [ -e "$INSTALL_DIR/config/$_name" ]; then
        _KEPT_CONF="${_KEPT_CONF} $_name"
        continue
    fi
    cp "$_f" "$INSTALL_DIR/config/$_name"
    if [ "$_name" = "tsurugi-belayer.conf" ]; then
        sed -i \
            -e "s|##BELAYER_INSTALL_DIR##|${INSTALL_DIR}|g" \
            -e "s|##TSURUGI_HOME##|${TSURUGI_HOME}|g" \
            -e "s|##TSURUGI_CONF##|${TSURUGI_CONF}|g" \
            -e "s|##TSURUGI_URL##|${TSURUGI_URL}|g" \
            -e "s|##BELAYER_STORAGE_ROOT##|${STORAGE_ROOT}|g" \
            -e "s|##BELAYER_SERVER_PORT##|${SERVER_PORT}|g" \
            -e "s|##BELAYER_MANAGEMENT_PORT##|${MANAGEMENT_PORT}|g" \
            "$INSTALL_DIR/config/$_name"
    fi
done

chmod u+x $INSTALL_DIR/jar/*.jar
chmod u+x $INSTALL_DIR/bin/*.sh
chmod u+x $INSTALL_DIR/systemd/*.sh
chmod u+x $TSURUGI_HOME/bin/*_w

echo "install dir: $INSTALL_DIR"
if [ -n "${_KEPT_CONF}" ]; then
    echo "kept the settings already in $INSTALL_DIR/config:${_KEPT_CONF}"
    echo "(the options of this run are not applied to them)"
else
    echo "TsurugiDB: $TSURUGI_CONF ($TSURUGI_URL)"
    echo "ports: $SERVER_PORT (management $MANAGEMENT_PORT)"
    echo "storage root: $STORAGE_ROOT"
fi
echo "installed."
echo ""
echo "start Belayer:"
echo "  $INSTALL_DIR/bin/start_server.sh"
echo "or register Belayer and the TsurugiDB as systemd services (as root):"
echo "  sudo $INSTALL_DIR/systemd/register_service.sh"

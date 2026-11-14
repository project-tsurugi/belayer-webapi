#!/bin/bash
#
# Registers this Belayer and its TsurugiDB as systemd services. The third,
# optional step of the installation; run it as root after install.sh.
#
# Both run as the owner of the install directory, which has to be the owner of
# the TsurugiDB as well. The TsurugiDB gets a unit of its own, and Belayer
# starts it with "sudo systemctl start" of that unit (bin/tgctl_dispatch.sh),
# so that it runs in the cgroup of its own unit: stopping, restarting or a
# crash of the Belayer service leaves the TsurugiDB running. The user may run
# exactly that command without a password (an entry in /etc/sudoers.d).

UNIT_DIR=/etc/systemd/system
SUDOERS_DIR=/etc/sudoers.d
SYSTEMCTL=/usr/bin/systemctl

usage() {
  cat <<EOF 1>&2
Usage: sudo $(basename $0) [OPTION]...

  --name=<name>        name that tells the services of this Belayer apart:
                       tsurugi-belayer-<name>.service, tsurugidb-<name>.service
                       and tsurugi-auth-<name>.service.
                       [default: none (tsurugi-belayer.service, ...) when the
                       install belongs to root, the name of the owner otherwise]
  --tsurugidb-only     register the TsurugiDB alone; Belayer is started by
                       hand (bin/start_server.sh) and still starts the
                       TsurugiDB through its unit.
  --with-auth-server   also register the authentication server (harinoki) of
                       the TsurugiDB. The TsurugiDB unit then starts it, but
                       stopping the TsurugiDB does not stop it.
  --no-enable          do not enable the services (start at boot).
  -h, --help           display this help and exit
EOF
}

# conf_value <file> <name> : prints the value of NAME=value in the conf.
conf_value() {
  sed -n "s/^$1=//p" "$2" | tail -n 1
}

# fill <template> <unit file> : writes the unit with the placeholders filled.
fill() {
  sed -e "s|##BELAYER_INSTALL_DIR##|${INSTALL_DIR}|g" \
      -e "s|##TSURUGI_HOME##|${TSURUGI_HOME}|g" \
      -e "s|##TSURUGI_CONF##|${TSURUGI_CONF}|g" \
      -e "s|##SERVICE_USER##|${SERVICE_USER}|g" \
      -e "s|##SERVICE_GROUP##|${SERVICE_GROUP}|g" \
      -e "s|##TSURUGIDB_UNIT##|${DB_UNIT}|g" \
      -e "s|##AUTH_UNIT##|${AUTH_UNIT_REF}|g" \
      -e "s|##AUTH_URL##|${AUTH_URL}|g" \
      "$1" > "$2"
}

# ini_value <file> <section> <key> : prints the value of the key in the
# section of an ini file (the last one when repeated), or nothing.
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

# set_conf <name> <value> : sets NAME=value in tsurugi-belayer.conf, keeping
# the owner of the file.
set_conf() {
  if grep -q "^$1=" "${CONF}"; then
    sed -i "s|^$1=.*|$1=$2|" "${CONF}"
  else
    echo "$1=$2" >> "${CONF}"
  fi
  chown "${SERVICE_USER}:${SERVICE_GROUP}" "${CONF}"
}

NAME=""
NAME_SET=false
TSURUGIDB_ONLY=false
WITH_AUTH=false
ENABLE=true
while [ $# -gt 0 ]; do
  case "$1" in
    --name=*) NAME=${1#--name=}; NAME_SET=true ;;
    --tsurugidb-only) TSURUGIDB_ONLY=true ;;
    --with-auth-server) WITH_AUTH=true ;;
    --no-enable) ENABLE=false ;;
    -h | --help) usage; exit 0 ;;
    *) echo "[ERROR] unknown option: $1" 1>&2; usage; exit 1 ;;
  esac
  shift
done

if [ "$(id -u)" -ne 0 ]; then
  echo "[ERROR] run $(basename $0) as root." 1>&2
  exit 1
fi

INSTALL_DIR=$(cd "$(dirname "$0")/.." && pwd)
CONF=${INSTALL_DIR}/config/tsurugi-belayer.conf
if [ ! -f "${CONF}" ]; then
  echo "[ERROR] ${CONF} is not found. run install.sh first." 1>&2
  exit 1
fi
TSURUGI_HOME=$(conf_value TSURUGI_HOME "${CONF}")
TSURUGI_CONF=$(conf_value TSURUGI_CONF "${CONF}")
TSURUGI_CONF=${TSURUGI_CONF:-${TSURUGI_HOME}/var/etc/tsurugi.ini}
if [ ! -x "${TSURUGI_HOME}/bin/tgctl" ]; then
  echo "[ERROR] TSURUGI_HOME in ${CONF} is not a TsurugiDB: ${TSURUGI_HOME}" 1>&2
  exit 1
fi
if [ "$(conf_value TSURUGI_TGCTL_COMMAND "${CONF}")" != "${INSTALL_DIR}/bin/tgctl_dispatch.sh" ]; then
  echo "[ERROR] TSURUGI_TGCTL_COMMAND in ${CONF} is not ${INSTALL_DIR}/bin/tgctl_dispatch.sh:" 1>&2
  echo "[ERROR] Belayer would start the TsurugiDB outside its unit." 1>&2
  exit 1
fi

# Belayer and the TsurugiDB run as the same user, the owner of both.
SERVICE_USER=$(stat -c %U "${INSTALL_DIR}")
SERVICE_GROUP=$(id -gn "${SERVICE_USER}")
TG_USER=$(stat -L -c %U "${TSURUGI_HOME}")
if [ "${TG_USER}" != "${SERVICE_USER}" ]; then
  echo "[ERROR] ${INSTALL_DIR} belongs to ${SERVICE_USER}, but the TsurugiDB ${TSURUGI_HOME} to ${TG_USER}." 1>&2
  echo "[ERROR] Belayer and the TsurugiDB run as the same user: install Belayer again as ${TG_USER}." 1>&2
  exit 1
fi

if ! ${NAME_SET} && [ "${SERVICE_USER}" != "root" ]; then
  NAME=${SERVICE_USER}
fi
case "${NAME}" in
  *[!A-Za-z0-9_.-]*)
    echo "[ERROR] --name may have letters, digits, '_', '.' and '-' only: ${NAME}" 1>&2
    exit 1
    ;;
esac
SUFFIX=${NAME:+-${NAME}}
BL_UNIT=tsurugi-belayer${SUFFIX}.service
DB_UNIT=tsurugidb${SUFFIX}.service
AUTH_UNIT=tsurugi-auth${SUFFIX}.service
AUTH_UNIT_REF=""
if ${WITH_AUTH}; then
  AUTH_UNIT_REF=${AUTH_UNIT}
  if [ ! -x "${TSURUGI_HOME}/bin/authentication-server" ]; then
    echo "[ERROR] ${TSURUGI_HOME}/bin/authentication-server is not found." 1>&2
    exit 1
  fi
  if ! command -v curl > /dev/null 2>&1; then
    echo "[ERROR] curl is not found. run install_packages.sh first." 1>&2
    exit 1
  fi
fi
# The unit of the authentication server waits until it answers here.
AUTH_URL=$(ini_value "${TSURUGI_CONF}" authentication url)
AUTH_URL=${AUTH_URL:-http://localhost:8080/harinoki}
case "${AUTH_URL}" in
  *[!A-Za-z0-9:/._~-]*)
    if ${WITH_AUTH}; then
      echo "[ERROR] [authentication] url of ${TSURUGI_CONF} has a character not supported here: ${AUTH_URL}" 1>&2
      exit 1
    fi
    ;;
esac
SUDOERS_FILE=${SUDOERS_DIR}/tsurugi-belayer${SUFFIX}

# Units of the same name for another TsurugiDB: two TsurugiDBs need two names.
if [ -f "${UNIT_DIR}/${DB_UNIT}" ] && ! grep -qF -- "--conf ${TSURUGI_CONF} " "${UNIT_DIR}/${DB_UNIT}"; then
  echo "[ERROR] ${DB_UNIT} already exists for another TsurugiDB:" 1>&2
  grep '^ExecStart=' "${UNIT_DIR}/${DB_UNIT}" 1>&2
  echo "[ERROR] give this one another name with --name=<name>." 1>&2
  exit 1
fi
if ! ${TSURUGIDB_ONLY} && [ -f "${UNIT_DIR}/${BL_UNIT}" ] && ! grep -qxF "EnvironmentFile=${CONF}" "${UNIT_DIR}/${BL_UNIT}"; then
  # Most likely the previous version of this Belayer: the unit moves to this one.
  echo "[INFO] ${BL_UNIT} of $(sed -n 's/^EnvironmentFile=//p' "${UNIT_DIR}/${BL_UNIT}") is replaced with this install."
  ${SYSTEMCTL} stop "${BL_UNIT}"
fi

# A TsurugiDB running outside its unit cannot be stopped or watched through it.
_TG_ACTIVE=false
${SYSTEMCTL} is-active --quiet "${DB_UNIT}" && _TG_ACTIVE=true
if ! ${_TG_ACTIVE} && runuser -u "${SERVICE_USER}" -- env TSURUGI_HOME="${TSURUGI_HOME}" \
    "${TSURUGI_HOME}/bin/tgctl" status --conf "${TSURUGI_CONF}" 2>&1 | grep -q RUNNING; then
  echo "[WARN] the TsurugiDB is running outside systemd. shut it down and start it with"
  echo "[WARN]   sudo systemctl start ${DB_UNIT}"
fi

# The sudoers entry: the user may start the TsurugiDB unit, nothing else.
if [ "${SERVICE_USER}" != "root" ]; then
  _tmp=$(mktemp)
  cat > "${_tmp}" <<EOF
# Written by ${INSTALL_DIR}/systemd/register_service.sh: Belayer starts the
# TsurugiDB through its unit.
${SERVICE_USER} ALL=(root) NOPASSWD: ${SYSTEMCTL} start ${DB_UNIT}
EOF
  if ! visudo -cqf "${_tmp}"; then
    echo "[ERROR] the sudoers entry did not pass visudo:" 1>&2
    cat "${_tmp}" 1>&2
    rm -f "${_tmp}"
    exit 1
  fi
  install -m 440 -o root -g root "${_tmp}" "${SUDOERS_FILE}"
  rm -f "${_tmp}"
fi

fill "${INSTALL_DIR}/systemd/tsurugidb.service" "${UNIT_DIR}/${DB_UNIT}"
_UNITS=${DB_UNIT}
if ${WITH_AUTH}; then
  fill "${INSTALL_DIR}/systemd/tsurugi-auth.service" "${UNIT_DIR}/${AUTH_UNIT}"
  _UNITS="${AUTH_UNIT} ${_UNITS}"
fi
if ! ${TSURUGIDB_ONLY}; then
  fill "${INSTALL_DIR}/systemd/tsurugi-belayer.service" "${UNIT_DIR}/${BL_UNIT}"
  _UNITS="${_UNITS} ${BL_UNIT}"
fi
set_conf BELAYER_TSURUGIDB_UNIT "${DB_UNIT}"

${SYSTEMCTL} daemon-reload
if ${ENABLE}; then
  ${SYSTEMCTL} enable ${_UNITS}
fi

echo "registered: ${_UNITS} (user ${SERVICE_USER}:${SERVICE_GROUP})"
if [ "${SERVICE_USER}" != "root" ]; then
  echo "sudoers: ${SUDOERS_FILE}"
fi
echo ""
echo "start them:"
for _u in ${_UNITS}; do
  echo "  sudo systemctl start ${_u}"
done
if ${TSURUGIDB_ONLY}; then
  echo "and restart Belayer (${INSTALL_DIR}/bin/start_server.sh) if it is running, to read BELAYER_TSURUGIDB_UNIT."
fi

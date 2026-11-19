#!/bin/bash
#
# Removes the systemd services that register_service.sh made for this Belayer.
# Run it as root. The names come from BELAYER_TSURUGIDB_UNIT in
# config/tsurugi-belayer.conf.
#
# The service of Belayer is removed only when it runs this install: after an
# upgrade, it runs the new version, which also goes on using the units of the
# TsurugiDB, so nothing is removed for the old one.

UNIT_DIR=/etc/systemd/system
SUDOERS_DIR=/etc/sudoers.d
SYSTEMCTL=/usr/bin/systemctl

usage() {
  cat <<EOF 1>&2
Usage: sudo $(basename $0) [OPTION]...

  --stop-tsurugidb   shut the TsurugiDB down when it runs, instead of stopping
                     with an error.
  -h, --help         display this help and exit
EOF
}

conf_value() {
  sed -n "s/^$1=//p" "$2" | tail -n 1
}

# remove_unit <unit> : stops, disables and removes the unit.
remove_unit() {
  [ -f "${UNIT_DIR}/$1" ] || return 0
  ${SYSTEMCTL} disable --now "$1"
  rm -f "${UNIT_DIR}/$1"
  echo "removed ${UNIT_DIR}/$1"
}

STOP_DB=false
while [ $# -gt 0 ]; do
  case "$1" in
    --stop-tsurugidb) STOP_DB=true ;;
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
DB_UNIT=$(conf_value BELAYER_TSURUGIDB_UNIT "${CONF}")
if [ -z "${DB_UNIT}" ]; then
  echo "no services are registered for ${INSTALL_DIR} (BELAYER_TSURUGIDB_UNIT is empty)."
  exit 0
fi
case "${DB_UNIT}" in
  tsurugidb*.service) SUFFIX=${DB_UNIT#tsurugidb}; SUFFIX=${SUFFIX%.service} ;;
  *)
    echo "[ERROR] BELAYER_TSURUGIDB_UNIT is not a unit register_service.sh makes: ${DB_UNIT}" 1>&2
    exit 1
    ;;
esac
BL_UNIT=tsurugi-belayer${SUFFIX}.service
AUTH_UNIT=tsurugi-auth${SUFFIX}.service
SUDOERS_FILE=${SUDOERS_DIR}/tsurugi-belayer${SUFFIX}

if [ -f "${UNIT_DIR}/${BL_UNIT}" ] && ! grep -qxF "EnvironmentFile=${CONF}" "${UNIT_DIR}/${BL_UNIT}"; then
  echo "${BL_UNIT} runs $(sed -n 's/^EnvironmentFile=//p' "${UNIT_DIR}/${BL_UNIT}"), not this install."
  echo "it and ${DB_UNIT} are left as they are."
else
  if ${SYSTEMCTL} is-active --quiet "${DB_UNIT}" && ! ${STOP_DB}; then
    echo "[ERROR] the TsurugiDB (${DB_UNIT}) is running. shut it down first, or use --stop-tsurugidb." 1>&2
    exit 1
  fi
  remove_unit "${BL_UNIT}"
  remove_unit "${DB_UNIT}"
  remove_unit "${AUTH_UNIT}"
  if [ -f "${SUDOERS_FILE}" ]; then
    rm -f "${SUDOERS_FILE}"
    echo "removed ${SUDOERS_FILE}"
  fi
  ${SYSTEMCTL} daemon-reload
fi

_owner=$(stat -c %U:%G "${CONF}")
sed -i "s|^BELAYER_TSURUGIDB_UNIT=.*|BELAYER_TSURUGIDB_UNIT=|" "${CONF}"
chown "${_owner}" "${CONF}"
echo "unregistered. Belayer of ${INSTALL_DIR} now starts the TsurugiDB by itself (bin/start_server.sh)."

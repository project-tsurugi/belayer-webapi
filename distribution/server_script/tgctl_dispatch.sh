#!/bin/bash
#
# The command Belayer runs for tgctl (TSURUGI_TGCTL_COMMAND). It decides how
# the TsurugiDB is started, and passes everything else on unchanged to
# BELAYER_TGCTL_DELEGATE (tgctl_w today, tgctl itself later).
#
# - BELAYER_TSURUGIDB_UNIT is empty: "start" runs the delegate in a session and
#   process group of its own, so that a signal sent to the group of Belayer
#   (Ctrl-C on a Belayer started from a terminal, for example) does not reach
#   the TsurugiDB.
# - BELAYER_TSURUGIDB_UNIT names a systemd unit: "start" starts that unit with
#   "sudo -n systemctl start", so that the TsurugiDB runs in the cgroup of its
#   own unit, not in the one of Belayer, and keeps running when the service of
#   Belayer stops, restarts or dies. The result is written to the monitor file
#   in place of tgctl. Shutting down still goes through the delegate (it has
#   the authentication token); the unit becomes inactive when the TsurugiDB
#   exits.
#
# Arguments are never passed through a shell.

DELEGATE=${BELAYER_TGCTL_DELEGATE:-${TSURUGI_HOME}/bin/tgctl_w}
UNIT=${BELAYER_TSURUGIDB_UNIT:-}
SYSTEMCTL=${BELAYER_SYSTEMCTL:-/usr/bin/systemctl}

if [ "$1" != "start" ]; then
    exec "${DELEGATE}" "$@"
fi

if [ -z "${UNIT}" ]; then
    exec setsid --wait "${DELEGATE}" "$@"
fi

# monitor_file <args...> : prints the value of --monitor.
monitor_file() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --monitor) echo "$2"; return ;;
            --monitor=*) echo "${1#--monitor=}"; return ;;
        esac
        shift
    done
}

MONITOR=$(monitor_file "$@")

# record <json fields> : appends one record to the monitor file, if any.
record() {
    [ -n "${MONITOR}" ] || return 0
    printf '{"timestamp":%s,%s}\n' "$(date +%s)" "$1" >> "${MONITOR}"
}

# finish_failure <message> : the message has no characters to escape in JSON.
finish_failure() {
    record "\"kind\":\"finish\",\"status\":\"failure\",\"message\":\"$1\""
    echo "$1" 1>&2
    exit 1
}

case "${UNIT}" in
    *[!A-Za-z0-9_.@:-]*) finish_failure "BELAYER_TSURUGIDB_UNIT is not a unit name" ;;
esac

record '"kind":"start"'
if "${SYSTEMCTL}" is-active --quiet "${UNIT}"; then
    finish_failure "${UNIT} is already active"
fi
sudo -n "${SYSTEMCTL}" start "${UNIT}"
rc=$?
if [ ${rc} -ne 0 ]; then
    finish_failure "sudo -n ${SYSTEMCTL} start ${UNIT} failed (exit ${rc}), see journalctl -u ${UNIT}"
fi
record '"kind":"finish","status":"success"'
exit 0

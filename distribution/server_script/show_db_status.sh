#!/bin/sh

OUTFILE=$1
CONFFILE=$2

# ${TSURUGI_HOME}/bin/tgctl config --conf ${CONFFILE} --monitor ${OUTFILE}
# ${TSURUGI_HOME}/bin/tgctl status --conf ${CONFFILE} --monitor ${OUTFILE}
# ${TSURUGI_HOME}/bin/tgha mode show --conf ${CONFFILE} --monitor ${OUTFILE}
# ${TSURUGI_HOME}/bin/tgha database version --conf ${CONFFILE} --monitor ${OUTFILE}

echo "{ \
       \"instance_id\": \"tsurugidb_t3\", \
       \"mode\": \"replica\", \
       \"status\": \"running\", \
       \"wal_version\": \"XXXXXXXX\" \
     }"

# instance_name and tags are not supplied from this script.
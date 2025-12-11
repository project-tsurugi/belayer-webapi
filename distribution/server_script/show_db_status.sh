#!/bin/sh

OUTFILE=$1
CONFFILE=$2

# ${TSURUGI_HOME}/bin/tgctl config --conf ${CONFFILE} --monitor ${OUTFILE}
# section=system key=instance_id value=<value> item_name=instance_id
# section=grpc_server key=enabled value=<value>  item_name=grpc_server_enabled
# section=grpc_server key=endpoint value=<value>  item_name=grpc_server_endpoint
# ${TSURUGI_HOME}/bin/tgctl status --conf ${CONFFILE} --monitor ${OUTFILE}
# kind=data status=<value>  item_name=status
# ${TSURUGI_HOME}/bin/tgha mode show --conf ${CONFFILE} --monitor ${OUTFILE}
# item_name=mode
# item_name=follows?
# ${TSURUGI_HOME}/bin/tgha database version --conf ${CONFFILE} --monitor ${OUTFILE}
# item_name=version

echo "{ \
       \"instance_id\": \"tsurugidb_t3\", \
       \"mode\": \"replica\", \
       \"status\": \"running\", \
       \"wal_version\": \"XXXXXXXX\", \
       \"follows\": \"dns:///master:50051\", \
       \"grpc_server_enabled\": true, \
       \"grpc_server_endpoint\": \"dns:///tsurugidb:50051\" \
     }"

# instance_name and tags are not supplied from this script.
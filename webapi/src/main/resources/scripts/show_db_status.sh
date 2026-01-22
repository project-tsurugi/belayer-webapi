#!/bin/bash
TSURUGI_TGCTL_COMMAND=$1
TSURUGI_TGHA_COMMAND=$2
OUTFILE=$3
CONFFILE=$4
AUTH_TOKEN=$5

${TSURUGI_TGCTL_COMMAND} status --conf ${CONFFILE} --monitor ${OUTFILE} --auth-token ${AUTH_TOKEN}
# kind=data status=<value>  key=status
OUT_JSON=$(cat $OUTFILE | jq -s '.[]|select(.format == "status")|{status: .status}')
STATUS=$(echo $OUT_JSON | jq -r ".status")

if [ "$STATUS" != "running" ]; then
  echo $OUT_JSON
  echo $OUT_JOSN >> show_db_status_out.log
  exit
fi

${TSURUGI_TGCTL_COMMAND} config --conf ${CONFFILE} --monitor ${OUTFILE} --auth-token ${AUTH_TOKEN}
# section=system key=instance_id value=<value> item_name=instance_id
# section=grpc_server key=enabled value=<value>  item_name=grpc_server_enabled
# section=grpc_server key=endpoint value=<value>  item_name=grpc_server_endpoint
ITEM_JSON=$(cat $OUTFILE | jq -s '.|map(select(.section == "system" and .key == "instance_id"))|map({key:.key, value:.value})|from_entries')
OUT_JSON=$(echo $OUT_JSON | jq ".|= .+$ITEM_JSON")
ITEM_JSON=$(cat $OUTFILE | jq -s '.|map(select(.section == "grpc_server" and .key == "enabled"))|map({key:"grpc_server_enabled", value:.value})|from_entries')
OUT_JSON=$(echo $OUT_JSON | jq ".|= .+$ITEM_JSON")
ITEM_JSON=$(cat $OUTFILE | jq -s '.|map(select(.section == "grpc_server" and .key == "endpoint"))|map({key:"grpc_server_endpoint", value:.value})|from_entries')
OUT_JSON=$(echo $OUT_JSON | jq ".|= .+$ITEM_JSON")

${TSURUGI_TGHA_COMMAND} mode show --conf ${CONFFILE} --monitor ${OUTFILE} --auth-token ${AUTH_TOKEN}
# key=mode
# key=master.replication_status
# key=replica.replication_status
# key=replica.upstream
# key=standby.replication_status

MODE=$(cat $OUTFILE | jq -s -r '.[]|select(.key == "mode")|.value')
OUT_JSON=$(echo $OUT_JSON | jq ".|= .+{mode: \"$MODE\"}")

if [ "${MODE}" = "" ]; then
   :
elif [ "${MODE}" = "replica" ]; then
    ITEM_JSON=$(cat $OUTFILE | jq -s '.|map(select(.key | IN("replica.replication_status", "replica.upstream")))|from_entries|{mode_status: ."replica.replication_status", follows: ."replica.upstream"}')
    OUT_JSON=$(echo $OUT_JSON | jq ".|= .+$ITEM_JSON")
else
    QUERY=".|map(select(.key | IN(\"${MODE}.replication_status\")))|map({key:.key, value:.value})|from_entries|{mode_status: .\"${MODE}.replication_status\"}"
    ITEM_JSON=$(cat $OUTFILE | jq -s "${QUERY}")
    OUT_JSON=$(echo $OUT_JSON | jq ".|= .+$ITEM_JSON")
fi

${TSURUGI_TGHA_COMMAND} database version --conf ${CONFFILE} --monitor ${OUTFILE} --auth-token ${AUTH_TOKEN}
ITEM_JSON=$(cat $OUTFILE | jq -s '.|map(select(.key == "version"))|map({key:.key, value:.value})|from_entries|{wal_version: .version}')
OUT_JSON=$(echo $OUT_JSON | jq ".|= .+$ITEM_JSON")

echo $OUT_JOSN >> show_db_status_out.log
echo $OUT_JSON

exit

# sample
echo "{ \
       \"instance_id\": \"tsurugidb_t3\", \
       \"mode\": \"replica\", \
       \"status\": \"running\", \
       \"mode_status\": \"ok\", \
       \"wal_version\": \"XXXXXXXX\", \
       \"follows\": \"dns:///master:50051\", \
       \"grpc_server_enabled\": true, \
       \"grpc_server_endpoint\": \"dns:///tsurugidb:50051\" \
     }"

# instance_name and tags are not supplied from this script.
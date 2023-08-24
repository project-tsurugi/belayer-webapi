#!/bin/bash

/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "create table demo(pk int primary key, col2 bigint, col3 float, col4 double, col5 char(4), cal6 varchar(10), col7 varchar(3))" --no-auth
/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "insert into demo(pk,col2,col3,col4,col5,cal6,col7) values (1,1,1.1,1.1,'1','1111', NULL)" --no-auth
/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "insert into demo(pk,col2,col3,col4,col5,cal6,col7) values (2,2,2.2,2.2,'2','2222', NULL)" --no-auth
/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "insert into demo(pk,col2,col3,col4,col5,cal6,col7) values (3,3,3.3,3.3,'3','3333', NULL)" --no-auth
/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "select * from demo" --no-auth

echo "check TsurugiDB is running..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

echo "shutdown TsurugiDB..."
/usr/lib/tsurugi/bin/oltp shutdown --conf /usr/lib/tsurugi/conf/tsurugi.ini
sleep 3

echo "check TsurugiDB is stopped..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

echo "call oltp quiesce to be exclusive mode ..."
/usr/lib/tsurugi/bin/oltp quiesce -conf /usr/lib/tsurugi/conf/tsurugi.ini
sleep 3

echo "check TsurugiDB status after calling oltp quiesce..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

echo "create backup..."

MONITOR_LOG_DIR=/tmp/bk_offline
BK_DATA_DIR=/tmp/bk_offline_data

mkdir $MONITOR_LOG_DIR
mkdir $BK_DATA_DIR
/usr/lib/tsurugi/bin/oltp backup create ${BK_DATA_DIR} --monitor ${MONITOR_LOG_DIR}/monitor.log --conf /usr/lib/tsurugi/conf/tsurugi.ini --force

echo ${BK_DATA_DIR}
echo "------------"
ls -al ${BK_DATA_DIR}
echo "------------"

echo "shutdown TsurugiDB..."
/usr/lib/tsurugi/bin/oltp shutdown --conf /usr/lib/tsurugi/conf/tsurugi.ini
sleep 5
echo "check TsurugiDB is stopped..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

/usr/lib/tsurugi/bin/oltp start --conf /usr/lib/tsurugi/conf/tsurugi.ini
sleep 3
/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "delete from demo" --no-auth
/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "select * from demo" --no-auth

/usr/lib/tsurugi/bin/oltp shutdown --conf /usr/lib/tsurugi/conf/tsurugi.ini
sleep 10
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

echo "restoring...."

/usr/lib/tsurugi/bin/oltp restore backup ${BK_DATA_DIR} --conf /usr/lib/tsurugi/conf/tsurugi.ini -monitor ${MONITOR_LOG_DIR}/restore-monitor.log --force 
#/usr/lib/tsurugi/bin/oltp restore backup ${BK_DATA_DIR} --conf /usr/lib/tsurugi/conf/tsurugi.ini -monitor ${MONITOR_LOG_DIR}/restore-monitor.log --force 2>&1 > /tmp/error.log &
#sleep 10
#tail ${MONITOR_LOG_DIR}/restore-monitor.log
#cat /tmp/error.log

echo "done."

echo "check TsurugiDB is stopped..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

echo "starting TsurugiDB..."
/usr/lib/tsurugi/bin/oltp start --conf /usr/lib/tsurugi/conf/tsurugi.ini
sleep 10

echo "check TsurugiDB is running..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "select * from demo" --no-auth

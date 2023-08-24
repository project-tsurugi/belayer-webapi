#!/bin/bash

/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "create table demo(pk int primary key, col2 bigint, col3 float, col4 double, col5 char(4), cal6 varchar(10), col7 varchar(3))" --no-auth
/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "insert into demo(pk,col2,col3,col4,col5,cal6,col7) values (1,1,1.1,1.1,'1','1111', NULL)" --no-auth
/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "select * from demo" --no-auth

echo "check TsurugiDB is running..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

echo "shutdown TsurugiDB..."
/usr/lib/tsurugi/bin/oltp shutdown --conf /usr/lib/tsurugi/conf/tsurugi.ini
sleep 10

echo "check TsurugiDB is stopped..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini


echo "restoring TsurugiDB..."
OUT_DIR=/tmp/bk1

unzip /usr/lib/tsurugi-webapp/storage/user1/bk1/backup.zip -d ${OUT_DIR}

LOG_DIR=/tmp/backup-log-1234
mkdir $LOG_DIR
/usr/lib/tsurugi/bin/oltp restore backup ${OUT_DIR} --conf /usr/lib/tsurugi/conf/tsurugi.ini -monitor ${LOG_DIR}/restore-monitor.log -force
sleep 10

echo "done."

echo "check TsurugiDB is stopped..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

echo "starting TsurugiDB..."
/usr/lib/tsurugi/bin/oltp start --conf /usr/lib/tsurugi/conf/tsurugi.ini
sleep 10

echo "check TsurugiDB is running..."
/usr/lib/tsurugi/bin/oltp status --conf /usr/lib/tsurugi/conf/tsurugi.ini

/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "select * from demo" --no-auth

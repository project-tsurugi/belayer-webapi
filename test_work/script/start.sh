#!/bin/bash

sleep 2

/usr/lib/tsurugi/bin/oltp start --conf /usr/lib/tsurugi/conf/tsurugi.ini

sleep 2

/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "select * from demo" --no-auth

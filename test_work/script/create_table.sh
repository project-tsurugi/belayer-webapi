#!/bin/bash

/usr/lib/tsurugi/bin/tgsql -c tcp://localhost:12345 --exec "create table demo(pk int primary key, col2 bigint, col3 float, col4 double, col5 char(4), cal6 varchar(10), col7 varchar(3))" --no-auth

/usr/lib/tsurugi/bin/tgsql -c tcp://localhost:12345 --exec "select * from demo" --no-auth

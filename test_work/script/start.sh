#!/bin/bash

/usr/lib/tsurugi/bin/tgctl start --conf /usr/lib/tsurugi/var/etc/tsurugi.ini

sleep 2

/usr/lib/tsurugi/bin/tgsql  -c tcp://localhost:12345 --exec "select * from demo" --no-auth

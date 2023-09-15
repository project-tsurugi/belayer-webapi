#!/bin/bash

/usr/lib/tsurugi/bin/tgsql -c tcp://localhost:12345 --exec "select * from demo" --no-auth

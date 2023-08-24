#!/bin/bash

/usr/lib/tsurugi/bin/tgsql exec -c tcp://localhost:12345 "select * from demo" --no-auth

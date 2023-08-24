#!/bin/bash

/usr/lib/tsurugi/bin/tgsql -c tcp://localhost:12345 -e UTF-8 --script ./create_data.sql --no-auth

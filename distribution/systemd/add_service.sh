#!/bin/sh

THIS_DIR=`dirname $0`
cp ${THIS_DIR}/tsurugi-belayer.service /etc/systemd/system
systemctl enable tsurugi-belayer.service
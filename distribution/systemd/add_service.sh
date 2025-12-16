#!/bin/sh

THIS_DIR=`dirname $0`
cp ${THIS_DIR}/tsurugi-webapp.service /etc/systemd/system
systemctl enable tsurugi-webapp.service
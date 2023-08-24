#!/bin/sh

/bin/cp tsurugi-webapp.service /etc/systemd/system
systemctl enable tsurugi-webapp.service
#!/bin/bash

export DEBCONF_NOWARNINGS=yes
sudo apt-get update && apt-get install -y curl
sudo apt-get remove -y cmdtest yarn
curl -fsSL https://deb.nodesource.com/setup_16.x | sudo bash -
echo "deb https://dl.yarnpkg.com/debian/ stable main" | sudo tee /etc/apt/sources.list.d/yarn.list
curl https://dl.yarnpkg.com/debian/pubkey.gpg | sudo apt-key add -
sudo apt-get update
sudo apt-get install -y nodejs yarn apt-utils
npx --yes playwright install
npx --yes playwright install-deps

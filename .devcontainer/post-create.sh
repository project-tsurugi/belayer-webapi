#!/bin/bash
#
# Runs once after the dev container is created (postCreateCommand).
# Puts what install.sh would put in place, from the working tree:
#   - tgctl_w / tgha_w into ${TSURUGI_HOME}/bin
#   - endpoints.csv / instance_info.json into ${BELAYER_HOME}/config
set -eu

cd "$(dirname "$0")/.."
WORKSPACE=$(pwd)

# The stubs are tracked without the execute bit, so copy them and set it
# (install.sh does the same with u+x).
for f in tgctl_w tgha_w; do
  cp "${WORKSPACE}/distribution/stub/${f}" "${TSURUGI_HOME}/bin/${f}"
  chmod u+x "${TSURUGI_HOME}/bin/${f}"
done

# Do not overwrite config files that were edited inside the container.
for f in endpoints.csv instance_info.json; do
  if [ ! -e "${BELAYER_HOME}/config/${f}" ]; then
    cp "${WORKSPACE}/distribution/config/${f}" "${BELAYER_HOME}/config/${f}"
  fi
done

echo "tgctl_w / tgha_w -> ${TSURUGI_HOME}/bin, config -> ${BELAYER_HOME}/config"

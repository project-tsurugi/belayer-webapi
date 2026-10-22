# Development container for belayer-webapi.
#
# Based on the official TsurugiDB image, so that the WebAPI runs next to a
# real TsurugiDB in the same container: Belayer connects over IPC and runs
# tgctl / tgctl_w / tgha_w locally. The image's entrypoint starts TsurugiDB
# and keeps running, and the dev container keeps that entrypoint.
ARG TSURUGI_VERSION=1.12.0
FROM ghcr.io/project-tsurugi/tsurugidb:${TSURUGI_VERSION}

USER root

# The image has Java 17 (JDK), Python 3.10 and curl. Add what the build, the
# status script (jq) and day-to-day development need.
RUN apt-get update -y && export DEBIAN_FRONTEND=noninteractive && \
    apt-get install -y --no-install-recommends \
      jq git sudo less unzip zip tzdata ca-certificates openssh-client && \
    rm -rf /var/lib/apt/lists/*

# Let the development user use sudo without a password.
RUN echo 'tsurugi ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/tsurugi && \
    chmod 0440 /etc/sudoers.d/tsurugi

# Belayer home (config/, proc/) and storage, owned by the user the WebAPI runs as.
RUN mkdir -p /opt/belayer/config /opt/belayer/proc /opt/belayer/storage && \
    chown -R tsurugi:tsurugi /opt/belayer

# Keep the Gradle cache in a volume (see docker-compose.yml).
RUN mkdir -p /home/tsurugi/.gradle && chown tsurugi:tsurugi /home/tsurugi/.gradle

USER tsurugi

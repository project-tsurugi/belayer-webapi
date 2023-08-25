FROM gradle:7.4.2-jdk11-focal as builder

ENV BELAYER_ROOT=/opt/belayer
ENV BELAYER_SRC_DIR=/opt/belayer/src
ENV BELAYER_DIST_DIR=/opt/belayer/dist
ENV TSURUGI_USER=tsurugi

COPY VERSION ${BELAYER_ROOT}/VERSION
COPY webapi ${BELAYER_SRC_DIR}

WORKDIR ${BELAYER_SRC_DIR}

ARG GPR_USER
ARG GPR_KEY
ENV GPR_USER=${GPR_USER}
ENV GPR_KEY=${GPR_KEY}

RUN gradle build -x test \
  && mkdir ${BELAYER_DIST_DIR} \
  && mv ./build/libs/belayer-webapi-`cat ../VERSION`.jar ${BELAYER_DIST_DIR}/belayer.jar


FROM ghcr.io/project-tsurugi/tsurugi:latest

ENV BELAYER_HOME=/usr/lib/tsurugi-webapp
ENV BELAYER_STORAGE_ROOT=${BELAYER_HOME}/storage
ENV TSURUGI_CONF_DIR=/usr/lib/tsurugi/conf
ENV BELAYER_DIST_DIR=/opt/belayer/dist
ENV HARINOKI_ETC=/usr/lib/tsurugi/var/auth/etc

USER root
RUN apt-get install -y jq

COPY --from=builder ${BELAYER_DIST_DIR}/belayer.jar ${BELAYER_HOME}/app/belayer.jar
COPY docker/startup.sh ${BELAYER_HOME}/bin/startup.sh
COPY test_work/script/* ${BELAYER_HOME}/bin/
RUN chmod +x ${BELAYER_HOME}/bin/*.sh

USER ${TSURUGI_USER}
WORKDIR /usr/lib/tsurugi-webapp/bin

COPY docker/harinoki-users.props ${HARINOKI_ETC}/harinoki-users.props
COPY docker/tsurugi.ini ${TSURUGI_CONF_DIR}/tsurugi.ini

ENV BELAYER_SERVER_PORT=8000
ENV BELAYER_MANAGEMENT_PORT=18000

EXPOSE 12345
EXPOSE 8000
EXPOSE 8080

ENTRYPOINT ["/usr/lib/tsurugi-webapp/bin/startup.sh"]


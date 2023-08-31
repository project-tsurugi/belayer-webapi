#!/bin/bash

cd `dirname $0`
THIS_DIR=`pwd`

VERSION=$(cat ../VERSION)
DIST_DIR=dist
DIST_TEMP_DIR=tsurugi-webapp-${VERSION}
DIST_ARCV_NAME=${DIST_TEMP_DIR}.tar.gz
JAR_NAME=tsurugi-webapp-${VERSION}.jar

echo "generate server distribution start...."
echo "version: $VERSION"

# create dist dir
rm -rf $DIST_DIR
mkdir -p $DIST_DIR

# create temp dir
rm -rf $DIST_TEMP_DIR
mkdir -p $DIST_TEMP_DIR

# WebAPI
mkdir -p $DIST_TEMP_DIR/app \
  && cd ../webapi \
  && sh gradlew build -x test \
  && cp ./build/libs/belayer-webapi-${VERSION}.jar ../distribution/$DIST_TEMP_DIR/app/${JAR_NAME} \
  && cd $THIS_DIR

if [ $? -ne 0 ]; then
    echo "Building Server App is failed."
    exit -1
fi

# scripts and replace version.
mkdir -p $DIST_TEMP_DIR/systemd
cp -p server_script/install.sh $DIST_TEMP_DIR/install.sh
cp -p server_script/start_server.sh $DIST_TEMP_DIR/start_server.sh
cp -p systemd/tsurugi-webapp.service $DIST_TEMP_DIR/systemd/tsurugi-webapp.service
cp -p systemd/add_service.sh $DIST_TEMP_DIR/systemd/add_service.sh
sed -i "s/#VERSION#/$VERSION/g" $DIST_TEMP_DIR/install.sh
sed -i "s/#VERSION#/$VERSION/g" $DIST_TEMP_DIR/start_server.sh
sed -i "s/#VERSION#/$VERSION/g" $DIST_TEMP_DIR/systemd/tsurugi-webapp.service
sed -i "s/#VERSION#/$VERSION/g" $DIST_TEMP_DIR/systemd/add_service.sh

# to tar.gz
tar zcf $DIST_DIR/$DIST_ARCV_NAME $DIST_TEMP_DIR

echo ""
echo "distribution has generated completely."

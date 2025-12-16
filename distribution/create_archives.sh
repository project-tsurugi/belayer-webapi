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
mkdir -p $DIST_TEMP_DIR/bin
mkdir -p $DIST_TEMP_DIR/config
mkdir -p $DIST_TEMP_DIR/systemd

cp -p install.sh $DIST_TEMP_DIR/
cp -p server_script/*.sh $DIST_TEMP_DIR/bin/
cp -p systemd/* $DIST_TEMP_DIR/systemd/
cp -p config/* $DIST_TEMP_DIR/config/
sed -i "s/#VERSION#/$VERSION/g" $DIST_TEMP_DIR/install.sh
chmod u+x $DIST_TEMP_DIR/install.sh
chmod u+x $DIST_TEMP_DIR/bin/*.sh

# to tar.gz
tar zcf $DIST_DIR/$DIST_ARCV_NAME $DIST_TEMP_DIR

echo ""
echo "distribution has generated completely."

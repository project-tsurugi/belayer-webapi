#!/bin/bash

cd `dirname $0`

cd ../remotecli
make build_stub

cd ../webapi
./gradlew build -x test -Dtsubakuro-auth=mock

VERSION=`cat ../VERSION`
java -Djava.io.tmpdir=`pwd`/../test_work/tmp -Dspring.profiles.active=ft -jar build/libs/belayer-webapi-${VERSION}.jar

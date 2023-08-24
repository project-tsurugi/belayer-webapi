#!/bin/bash

yarn install

# boot up tsurugi & belayer server
docker-compose down -v && docker-compose up -d --build && sleep 3 && docker-compose exec server ./insert_data.sh

yarn test --runInBand --detectOpenHandles --silence=false

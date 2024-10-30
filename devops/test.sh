#!/usr/bin/env bash

set -e
set -x

source ./devops/.env.sh

sbt -batch -no-colors -v \
  "$VERSION_COMMAND clean" \
  "$VERSION_COMMAND Test/compile" \
  "$VERSION_COMMAND test"

docker rm $(docker ps -aq) || true
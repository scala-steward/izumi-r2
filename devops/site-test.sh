#!/usr/bin/env bash

set -e
set -x

source ./devops/.env.sh

sbt -batch -no-colors -v \
  --java-home "$JAVA_HOME" \
  "project docs" \
  "$VERSION_COMMAND clean" \
  "$VERSION_COMMAND makeSite"
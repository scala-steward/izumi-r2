#!/usr/bin/env bash

set -euo pipefail

function run-site-publish() {
  source ./devops/.validate-publishing.sh
  
  sbt -batch -no-colors -v \
    --java-home "$JAVA_HOME" \
    "project docs" \
    "$VERSION_COMMAND clean" \
    "$VERSION_COMMAND makeSite" \
    "$VERSION_COMMAND ghpagesSynchLocal" \
    "$VERSION_COMMAND ghpagesPushSite"
}
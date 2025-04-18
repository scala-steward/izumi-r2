#!/usr/bin/env bash

set -euo pipefail

step_enable run-test

for arg in "$@" ; do case $arg in
    *)
        ;;
esac done
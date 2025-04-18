#!/usr/bin/env bash

set -euo pipefail

step_enable run-gen-js

for arg in "$@" ; do case $arg in
    *)
        ;;
esac done
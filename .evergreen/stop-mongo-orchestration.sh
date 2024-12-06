#!/bin/bash

set -o errexit

if [[ -z "$MONGO_ORCHESTRATION_HOME" ]]; then
    echo >&2 "\$MONGO_ORCHESTRATION_HOME must be set"
    exit 1
fi

cd ${MONGO_ORCHESTRATION_HOME}
# source the mongo-orchestration virtualenv if it exists
if [ -f venv/bin/activate ]; then
  . venv/bin/activate
elif [ -f venv/Scripts/activate ]; then
  . venv/Scripts/activate
fi
mongo-orchestration stop || true
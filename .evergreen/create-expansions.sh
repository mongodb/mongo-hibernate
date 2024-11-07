#!/bin/bash

# Get the current unique version of this checkout.
if [[ "$is_patch" = true ]]; then
  CURRENT_VERSION=$(git describe)-patch-${version_id}
else
  CURRENT_VERSION=latest
fi

DRIVERS_TOOLS="$(pwd)/../drivers-tools"

# Python has cygwin path problems on Windows. Detect prospective mongo-orchestration home
# directory.
if [[ "${OS}" = "Windows_NT" ]]; then
  export DRIVERS_TOOLS=$(cygpath -m $DRIVERS_TOOLS)
fi

MONGO_ORCHESTRATION_HOME="${DRIVERS_TOOLS}/.evergreen/orchestration"
MONGODB_BINARIES="${DRIVERS_TOOLS}/mongodb/bin"
PROJECT_DIRECTORY="$(pwd)"
TMPDIR="${MONGO_ORCHESTRATION_HOME}/db"
PATH="${MONGODB_BINARIES}:${PATH}"
PROJECT="${project}"

cat <<EOT >expansion.yml
CURRENT_VERSION: "${CURRENT_VERSION}"
DRIVERS_TOOLS: "${DRIVERS_TOOLS}"
MONGO_ORCHESTRATION_HOME: "${MONGO_ORCHESTRATION_HOME}"
MONGODB_BINARIES: "${MONGODB_BINARIES}"
PROJECT_DIRECTORY: "${PROJECT_DIRECTORY}"
TMPDIR: "${TMPDIR}"
PATH: "${PATH}"
PROJECT: "${PROJECT}"
PREPARE_SHELL: |
    set -o errexit
    set -o xtrace

    export DRIVERS_TOOLS="${DRIVERS_TOOLS}"
    export MONGO_ORCHESTRATION_HOME="${MONGO_ORCHESTRATION_HOME}"
    export MONGODB_BINARIES="${MONGODB_BINARIES}"
    export PROJECT_DIRECTORY="${PROJECT_DIRECTORY}"

    export TMPDIR="${TMPDIR}"
    export PATH="${PATH}"
    export PROJECT="${PROJECT}"

EOT

# Print the expansion file created.
cat expansion.yml

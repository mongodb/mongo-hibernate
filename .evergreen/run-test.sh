#!/bin/bash

set -o xtrace   # Write all commands first to stderr
set -o errexit  # Exit the script with error if any of the commands fail

############################################
#            Main Program                  #
############################################

source java-config.sh

echo "${PROJECT}: running integration tests ..."

echo "MongoDB version: ${MONGODB_VERSION}; topology: ${TOPOLOGY}"

echo "Running tests with Java ${JAVA_VERSION}"

./gradlew -version

./gradlew -PjavaVersion=${JAVA_VERSION} --stacktrace --info --continue clean test

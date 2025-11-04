#!/usr/bin/env bash

set -o xtrace   # Write all commands first to stderr
set -o errexit  # Exit the script with error if any of the commands fail

############################################
#            Main Program                  #
############################################

source java-config.sh

echo "mongo-hibernate: static checking ..."

./gradlew -version

./gradlew --info -x test -x integrationTest -x spotlessApply clean check compileJava

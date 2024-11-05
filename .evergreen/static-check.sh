#!/bin/bash

set -o xtrace   # Write all commands first to stderr
set -o errexit  # Exit the script with error if any of the commands fail

############################################
#            Main Program                  #
############################################
source java-config.sh

echo "mongo-hibernate: static checking ..."

./gradlew -version
./gradlew -PxmlReports.enabled=true --info -x test clean check compileJava

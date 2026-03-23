#!/usr/bin/env bash

set -o xtrace   # Write all commands first to stderr
set -o errexit  # Exit the script with error if any of the commands fail

############################################
#            Main Program                  #
############################################

source java-config.sh

echo "mongo-hibernate: running smoke tests ..."

echo "MongoDB version: ${MONGODB_VERSION}; topology: ${TOPOLOGY}"

./gradlew -version

./gradlew -PjavaVersion="${JAVA_VERSION}" publishToMavenLocal \
  && ./example-module/mvnw clean verify --file ./example-module/pom.xml \
    -DjavaVersion="${JAVA_VERSION}" \
    -DprojectVersion="$(./gradlew -q printProjectVersion)"

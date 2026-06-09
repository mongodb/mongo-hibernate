#!/usr/bin/env bash

# Java configurations for evergreen
# JAVA_HOME is required to launch Gradle itself (Gradle 9+ requires JDK 17+).
export JDK17="/opt/java/jdk17"

# The directory check allows to run CI scripts locally without
# JAVA_HOME pointing to a nonexistent path; gradlew falls back to PATH.
if [ -d "$JDK17" ]; then
  export JAVA_HOME=$JDK17
fi

export JAVA_VERSION=${JAVA_VERSION:-17}

echo "Java Configs:"
echo "Java Home: ${JAVA_HOME}"
echo "Java test version: ${JAVA_VERSION}"

#!/usr/bin/env bash

# Java configurations for evergreen

export JDK17="/opt/java/jdk17"
export JDK21="/opt/java/jdk21"

if [ -d "$JDK17" ]; then
  export JAVA_HOME=$JDK17
fi

export JAVA_VERSION=${JAVA_VERSION:-17}

echo "Java Configs:"
echo "Java Home: ${JAVA_HOME}"
echo "Java test version: ${JAVA_VERSION}"

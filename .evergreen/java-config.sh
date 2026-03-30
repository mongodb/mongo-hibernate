#!/usr/bin/env bash

# Java configurations for evergreen

# JAVA_HOME is required to launch Gradle itself (Gradle 9+ requires JDK 17+).
export JAVA_HOME="/opt/java/jdk17"
export JAVA_VERSION=${JAVA_VERSION:-17}

echo "Java Configs:"
echo "Java Home: ${JAVA_HOME}"
echo "Java test version: ${JAVA_VERSION}"

# MongoDB Extension for Hibernate ORM

<p align="right">
  <a href="https://docs.oracle.com/en/java/javase/17/">
    <img src="https://img.shields.io/badge/Java_SE-17+-E49639.svg?labelColor=32728B"
        alt="Java SE requirement">
  </a>
  <a href="https://hibernate.org/orm/documentation/6.6/">
    <img src="https://img.shields.io/badge/Hibernate_ORM-6.6-BAAE80.svg?labelColor=5C656C"
        alt="Hibernate ORM requirement">
  </a>
  <a href="https://www.mongodb.com/docs/manual/">
    <img src="https://img.shields.io/badge/MongoDB_-7.0+-00ED64.svg?labelColor=001E2B"
        alt="MongoDB DBMS requirement">
  </a>
</p>

## Overview

This product enables applications to use databases managed by the [MongoDB](https://www.mongodb.com/) DBMS
via [Hibernate ORM](https://hibernate.org/orm/).

MongoDB speaks [MQL (**M**ongoDB **Q**uery **L**anguage)](https://www.mongodb.com/docs/manual/reference/mql/)
instead of SQL. This product works by:

- Creating a JDBC adapter using [MongoDB Java Driver](https://www.mongodb.com/docs/drivers/java-drivers/),
  which has to be plugged into Hibernate ORM via a custom
  [`ConnectionProvider`](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/engine/jdbc/connections/spi/ConnectionProvider.html).
- Translating Hibernate's internal SQL AST into MQL by means of a custom
  [`Dialect`](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/dialect/Dialect.html),
  which has to be plugged into Hibernate ORM.

## User Documentation

- [Manual](https://www.mongodb.com/docs/languages/java/mongodb-hibernate/current)
- [API](https://javadoc.io/doc/org.mongodb/mongodb-hibernate/latest/index.html)

MongoDB [standalone deployments](https://www.mongodb.com/docs/manual/reference/glossary/#std-term-standalone) are not supported,
because they [do not support transactions](https://www.mongodb.com/docs/manual/core/transactions-production-consideration/).
If you use one, you may [convert it to a replica set](https://www.mongodb.com/docs/manual/tutorial/convert-standalone-to-replica-set/).

### Maven Artifacts

The `groupId:artifactId` coordinates: `org.mongodb:mongodb-hibernate`.

  - [Maven Central Repository](https://repo.maven.apache.org/maven2/org/mongodb/mongodb-hibernate/)
  - [Maven Central Repository Search](https://central.sonatype.com/artifact/org.mongodb/mongodb-hibernate)

### Examples

[Maven](https://maven.apache.org/) is used as a build tool.

The Java module with example applications is located in

- [`./example-module`](example-module)

The examples may be run by running the smoke tests as specified in [Run Smoke Tests](#run-smoke-tests).

### Bug Reports

Use ["Extension for Hibernate ORM" at jira.mongodb.org](https://jira.mongodb.org/projects/HIBERNATE/issues).

### Feature Requests

Use ["Drivers & Frameworks"/"Frameworks (e.g. Django, Hibernate, EFCore)" at feedback.mongodb.com" at feedback.mongodb.com](https://feedback.mongodb.com/?category=7548141831345841376).

## Contributor Documentation

[Gradle](https://gradle.org/) is used as a build tool.

### Build from Source

```console
./gradlew build
```

### Static Code Analysis

#### Code Formatting

[Spotless](https://github.com/diffplug/spotless)
[Gradle plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle) is used as a general-purpose formatting tool,
[Palantir Java Format](https://github.com/palantir/palantir-java-format) is used as a Java-specific formatting tool
integrated with it.

##### Check Code Formatting

```console
./gradlew spotlessCheck
```

##### Format Code

```console
./gradlew spotlessApply
```

#### Code Quality

[Error Prone](https://errorprone.info/) [Gradle plugin](https://github.com/tbroyer/gradle-errorprone-plugin)
is used as a Java-specific code analysis tool,
[NullAway](https://github.com/uber/NullAway) is used as a
[`NullPointerException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NullPointerException.html)
prevention tool integrated with it. [JSpecify](https://jspecify.dev) annotations are used to specify nullness.

The analysis is done as part of the Gradle `compileJava` task execution.

### Testing

This project uses separate directories for unit, integration, smoke tests:

- [`./src/test`](src/test)
- [`./src/integrationTest`](src/integrationTest)
- [`./example-module/src/smokeTest`](example-module/src/smokeTest)

#### Run Unit Tests

```console
./gradlew test
```

#### Run Integration Tests

The integration tests require a MongoDB deployment that

- is accessible at `localhost:27017`;
  - You may change the [MongoDB connection string](https://www.mongodb.com/docs/manual/reference/connection-string/)
    via the [`jakarta.persistence.jdbc.url`](https://docs.hibernate.org/orm/6.6/userguide/html_single/#settings-jakarta.persistence.jdbc.url)
    configuration property
    in [`./src/integrationTest/resources/hibernate.properties`](src/integrationTest/resources/hibernate.properties). 
- has test commands enabled.
  - This may be achieved with the
    [`--setParameter enableTestCommands=1`](https://www.mongodb.com/docs/manual/reference/parameters/)
    command-line arguments.

```console
./gradlew integrationTest
```

#### Run Smoke Tests

The smoke tests with the `Tests` suffix do not require a MongoDB deployment.
The smoke tests with the `IntegrationTests` suffix, as well as the examples, require a MongoDB deployment that

- is accessible at `localhost:27017`.
  - You may change this by modifying the examples run by the smoke tests.

```console
source ./.evergreen/java-config.sh \
  && ./gradlew -PjavaVersion=${JAVA_VERSION} publishToMavenLocal \
  && ./example-module/mvnw verify -f ./example-module/pom.xml \
    -DjavaVersion="${JAVA_VERSION}" \
    -DprojectVersion="$(./gradlew -q printProjectVersion)"
```

### Continuous Integration
[Evergreen](https://github.com/evergreen-ci/evergreen) and [GitHub Actions](https://docs.github.com/en/actions)
are used for continuous integration.

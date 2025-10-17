# MongoDB Extension for Hibernate ORM

This product enables applications to use databases managed by the [MongoDB](https://www.mongodb.com/) DBMS
via [Hibernate ORM](https://hibernate.org/orm/).

## Overview

MongoDB speaks MQL (**M**ongoDB **Q**uery **L**anguage) instead of SQL. This product works by:

- Creating a JDBC adapter using [MongoDB Java Driver](https://www.mongodb.com/docs/drivers/java-drivers/),
  which has to be plugged into Hibernate ORM via a custom [`ConnectionProvider`](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/engine/jdbc/connections/spi/ConnectionProvider.html).
- Translating Hibernate's internal SQL AST into MQL by means of a custom [`Dialect`](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/dialect/Dialect.html),
  which has to be plugged into Hibernate ORM.

## Development

Java 17 is the JDK version for development.

Initially Hibernate ORM v6.6 is the dependency version.

MongoDB v6.0 is the minimal version this product supports.

> [Standalone instance](https://www.mongodb.com/docs/manual/reference/glossary/#std-term-standalone) is not supported. It is recommended to [convert it to a replica set](https://www.mongodb.com/docs/manual/tutorial/convert-standalone-to-replica-set/).

### Build from Source

#### Static Code Analysis

#### Code Style Check

We chose [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-gradle) as a general-purpose formatting plugin, and [Palantir Java Format](https://github.com/palantir/palantir-java-format) as a Java-specific formatting tool integrated with it.

To check whether any format violation exists, run `spotlessCheck` gradle task. If any format violation is found during the previous step, run `spotlessApply` auto-formatting task to fix it automatically.

#### Code Quality Check

[Error Prone](https://github.com/tbroyer/gradle-errorprone-plugin) gradle plugin is chosen for Java code qualify analysis during Java compiling phrase. [NullAway](https://github.com/uber/NullAway) is a Java `NullPointerException`s (NPEs) prevention gradle plugin integrated with Error Prone. [JSpecify](https://jspecify.dev) annotations are used to help NullAway detect potential NPEs.

Both plugins are enabled on gradle's `compileJava` task.

### Testing

This project uses separate directories for unit and integration tests:

- [unit test](src/test)
- [integration test](src/integrationTest)

#### Gradle Tasks

##### Unit Test
```console
./gradlew clean test
```

##### Integration Test
```console
./gradlew clean integrationTest
```

Integration tests require a MongoDB deployment with test commands enabled,
which may be achieved with the
[`--setParameter enableTestCommands=1`](https://www.mongodb.com/docs/manual/reference/parameters/)
command-line arguments.
You may change the default [MongoDB connection string](https://www.mongodb.com/docs/manual/reference/connection-string/) as below, in [hibernate.properties](src/integrationTest/resources/hibernate.properties):

```properties
jakarta.persistence.jdbc.url={your_mongodb_connection_string}
```

### CI/CD
This project uses [evergreen](https://github.com/evergreen-ci/evergreen), a distributed continuous integration system from MongoDB. The evergreen configuration is in the [.evergreen](/.evergreen) directory.

## References
- [An Introduction to Hibernate 6](https://docs.jboss.org/hibernate/orm/6.6/introduction/html_single/Hibernate_Introduction.html)
- [A Guide to Hibernate Query Language](https://docs.jboss.org/hibernate/orm/6.6/querylanguage/html_single/Hibernate_Query_Language.html)
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html)
# A MongoDB Dialect for the Hibernate ORM

This project aims to provide a library to seamlessly integrate MongoDB with Hibernate ORM. Hibernate _ORM_ is a powerful **O**bject-**r**elational **m**apping tool. Due to the SQL and JDBC standards, Hibernate ORM could centralize each SQL vendor's idiosyncrasies in the so-called _Hibernate Dialect_. This project will include a document database member in the Hibernate's Dialect family.

## Overview

MongoDB speaks _MQL_ (**M**ongoDB **Q**uery **L**anguage in JSON format) instead of SQL. This project creates a MongoDB Hibernate Dialect by:

- Creating a JDBC adapter using [MongoDB Java Driver](https://www.mongodb.com/docs/drivers/java-drivers/)
- Translating Hibernate's internal SQL AST into MQL

<img src="mongodb_dialect.png" alt="MongoDB Dialect" />

## Development

Java 17 is the JDK version for development.

Initially Hibernate ORM v6.6 is the dependency version.

MongoDB v6 is the minimal version this dialect supports.

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

Per best practice, we maintain separate directories for unit and integration testings:

- [unit test](src/test)
- [integration test](src/integrationTest)

Integration tests will connect to a MongoDB deployment. You may change the default [MongoDB connection string](https://www.mongodb.com/docs/manual/reference/connection-string/) configured as below at [hibernate.properties](src/integrationTest/resources/hibernate.properties):

```properties
jakarta.persistence.jdbc.url=mongodb://localhost/mongo-hibernate-test?directConnection=false
```

### CI/CD
An internal CI/CD pipeline is based on an open-source project [evergreen](https://github.com/evergreen-ci/evergreen), a distributed continuous integration system from MongoDB. The corresponding evergreen configuration resources reside in a [.evergreen](/.evergreen) directory under the project root folder.

## References
It would be highly helpful to refer to the following awesome articles or resources when need arises:
- [An Introduction to Hibernate 6](https://docs.jboss.org/hibernate/orm/6.6/introduction/html_single/Hibernate_Introduction.html)
- [A Guide to Hibernate Query Language](https://docs.jboss.org/hibernate/orm/6.6/querylanguage/html_single/Hibernate_Query_Language.html)
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html)
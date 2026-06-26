# MongoDB Extension for Hibernate ORM

<p align="right">
  <a href="https://docs.oracle.com/en/java/javase/17/">
    <img src="https://img.shields.io/badge/Java_SE-17+-E49639.svg?labelColor=32728B"
        alt="Java SE requirement"/></a>
  <a href="https://hibernate.org/orm/documentation/6.6/">
    <img src="https://img.shields.io/badge/Hibernate_ORM-6.6-BAAE80.svg?labelColor=5C656C"
        alt="Hibernate ORM requirement"/></a>
  <a href="https://www.mongodb.com/docs/manual/">
    <img src="https://img.shields.io/badge/MongoDB_-7.0+-00ED64.svg?labelColor=001E2B"
        alt="MongoDB DBMS requirement"/></a>
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

### Spring Boot

A [Spring Boot](https://spring.io/projects/spring-boot) starter lets a Spring Boot 4.x application use
MongoDB through JPA. Add the starter

`org.mongodb:mongodb-hibernate-spring-boot-starter`

declare MongoDB as the JPA platform, and configure the connection with Spring Boot's standard
`spring.mongodb.*` properties (the value is a [MongoDB connection string](https://www.mongodb.com/docs/manual/reference/connection-string/)):

```properties
spring.jpa.database-platform=MongoDB
spring.mongodb.uri=mongodb://localhost/mydb
```

When `spring.jpa.database-platform` is `MongoDB`, the starter auto-configures a JPA
`EntityManagerFactory`, a `JpaTransactionManager`, and Spring Data JPA repositories backed by MongoDB.
The connection is configured through Spring Boot's `spring-boot-mongodb` support (the `spring.mongodb.*`
namespace), and the integration **borrows that `MongoClient`** rather than creating its own: a single
connection pool shared with health checks, metrics, and any other use of the Spring-managed client. This
reuses `spring-boot-mongodb`'s client infrastructure and is **not** Spring Data MongoDB.

The starter brings no SQL connection pool, so a MongoDB-only application needs no `spring.datasource.url`
and Spring Boot's `DataSourceAutoConfiguration` stays inert. Without `spring.jpa.database-platform=MongoDB`
the starter contributes nothing. It is safe to have on the classpath of a non-MongoDB application.

#### Requirements

The starter requires **Spring Boot 4.x** and **Hibernate ORM 7.4 or later** (the version the MongoDB
extension is built against). Spring Boot manages the Hibernate version through its BOM, and some
Spring Boot 4.x releases still ship an older Hibernate, for example, Spring Boot 4.0.6 manages
Hibernate 7.2.12. When the managed version is below 7.4, the application fails to start with a
`NoSuchMethodError` from the MongoDB dialect. Until your Spring Boot version's BOM ships Hibernate 7.4
or later, override the managed version:

Gradle (with the Spring Boot or `io.spring.dependency-management` plugin):

```groovy
ext['hibernate.version'] = '7.4.1.Final'
```

Maven:

```xml
<properties>
    <hibernate.version>7.4.1.Final</hibernate.version>
</properties>
```

#### Configuration

The standard Spring Boot JPA properties under `spring.jpa.*` are honored. For example
`spring.jpa.show-sql`, `spring.jpa.hibernate.ddl-auto`, and `spring.jpa.properties.*`, along with any
`HibernatePropertiesCustomizer` / `EntityManagerFactoryBuilderCustomizer` beans and
`spring.jpa.open-in-view` (Open Session in View, on by default in servlet web applications).

The MongoDB connection uses Spring Boot's `spring.mongodb.*` properties. To customize the borrowed
client further — connection pool sizing, command listeners, UUID representation, and so on — declare a
`MongoClientSettingsBuilderCustomizer` bean, exactly as for any other `spring-boot-mongodb` application.

#### Field naming

Unlike a SQL Spring Boot application, the default physical naming strategy is **Hibernate's own** (the
Java property name, e.g. `publishYear`) rather than Spring Boot's `snake_case`. MongoDB has no schema
to catch a naming mismatch, so a `snake_case` default could silently leave a collection holding a mix
of field names; keeping Hibernate's default means an entity persists the same way whether bootstrapped
through this starter or through plain Hibernate ORM. To use `snake_case` (or any other strategy), set
it explicitly:

```properties
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl
```

> **Note:** changing the naming strategy after documents have been written changes the field names of
> *new* documents only; existing documents are not migrated, which can leave a collection with mixed
> field names.

#### Limitations

- **Testing:** the `@DataJpaTest` slice is not supported (it wires Spring Boot's SQL JPA
  auto-configuration); use `@SpringBootTest` to test against MongoDB. Slice support is tracked by
  [HIBERNATE-179](https://jira.mongodb.org/browse/HIBERNATE-179).
- **Single persistence unit:** a single `@Primary` `EntityManagerFactory` is auto-configured.
- **Native image:** GraalVM native-image execution is not yet validated.
- A [replica set](https://www.mongodb.com/docs/manual/reference/glossary/#std-term-replica-set) is
  required, as transactions are (see [User Documentation](#user-documentation) above).

### Bug Reports

Use ["Extension for Hibernate ORM" at jira.mongodb.org](https://jira.mongodb.org/projects/HIBERNATE/issues).

### Feature Requests

Use ["Drivers & Frameworks"/"Frameworks (e.g. Django, Hibernate, EFCore)" at feedback.mongodb.com](https://feedback.mongodb.com/?category=7548141831345841376).

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
  && ./example-module/mvnw verify --file ./example-module/pom.xml \
    -DjavaVersion="${JAVA_VERSION}" \
    -DprojectVersion="$(./gradlew -q printProjectVersion)"
```

### Continuous Integration
[Evergreen](https://github.com/evergreen-ci/evergreen) and [GitHub Actions](https://docs.github.com/en/actions)
are used for continuous integration.

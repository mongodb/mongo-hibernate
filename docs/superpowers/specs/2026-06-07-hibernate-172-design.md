# HIBERNATE-172: Spring Boot auto-configuration and starter for MongoDB-backed JPA

## Goal

In a Spring Boot application, integrate MongoDB-backed JPA via the MongoDB Extension for Hibernate ORM
with:

- standard Spring configuration of the MongoDB connection (through `spring-boot-mongodb`),
- a single shared `MongoClient` (connection pool) — no second pool when the application also uses
  Spring's MongoDB support,
- the full `spring.jpa.*` property surface (`ddl-auto`, `show-sql`, naming strategies,
  `HibernatePropertiesCustomizer` / `EntityManagerFactoryBuilderCustomizer` beans),
- Spring Data JPA repository support without a SQL `DataSource`,
- a manual path for applications that want a dedicated client or multiple persistence units.

## Background

mongo-hibernate is the MongoDB Extension for Hibernate ORM: it plugs a `MongoDialect` and a
`MongoConnectionProvider` into Hibernate so JPA entities map to MongoDB. On its own it connects through
a `MongoClient` created by `MongoConnectionProvider`.

Spring Boot auto-configuration registers `@Configuration` classes that activate conditionally; Spring
Boot scans `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and loads
each listed class subject to its `@Conditional` guards. User-defined beans win, and a configuration
activates only when its prerequisites are met. The conditions used here are `@ConditionalOnClass`
(class on the classpath), `@ConditionalOnMissingBean` (no user bean of that type),
`@ConditionalOnBean` / `@ConditionalOnSingleCandidate` (a bean is present), and
`@ConditionalOnProperty` / `@ConditionalOnBooleanProperty`.

In a Spring Boot 4.x application Spring Boot does not create a `MongoClient` automatically: Spring Boot
4.0 moved MongoDB auto-configuration out of the monolithic `spring-boot-autoconfigure` into a separate
`spring-boot-mongodb` module that is not on the classpath unless added explicitly.

A second `MongoClient` is introduced only when the application pursues Spring-native client
customization. Applications and Spring customize a `MongoClient` through
`MongoClientSettingsBuilderCustomizer` beans (Micrometer metrics, SSL, UUID representation, event
listeners). That hook, and the `MongoConnectionDetails` connection infrastructure (Testcontainers,
Docker Compose, cloud bindings), live in `spring-boot-mongodb`. Using them requires
`spring-boot-mongodb` on the classpath, at which point its `MongoAutoConfiguration` creates a
`MongoClient` bean. Building a separate mongo-hibernate client from a connection URL would then yield
two clients — two connection pools to the same cluster (see [Rejected alternatives](#rejected-alternatives)).

This design keeps `spring-boot-mongodb` available for customization and keeps the client count at one,
by having `MongoConnectionProvider` borrow the Spring-managed client instead of building its own.

## Version compatibility

The integration targets Spring Boot 4.x and requires Hibernate ORM 7.3 or later; it uses Hibernate 7.3
APIs and does not run on earlier 7.x.

Spring Boot 3.x (Spring Framework 6.x, Hibernate ORM 6.x) is not compatible. Spring Boot 4.x is the
first generation that works with the extension, but its BOM may still manage a Hibernate version below
7.3 (for example, Spring Boot 4.0.6 manages Hibernate 7.2.x). Spring Boot's dependency management
forces the BOM's Hibernate version onto the application even though the extension requests 7.3, and
startup then fails with a `NoSuchMethodError` from the dialect. A library cannot raise a dependency
above the consumer's BOM, so this is resolved on the consumer side.

| Spring Boot | Hibernate ORM in its BOM | Works out of the box |
|---|---|---|
| 3.x | 6.x | No — incompatible |
| 4.x, BOM Hibernate < 7.3 | e.g. 7.2.x | Only after overriding `hibernate.version` to ≥ 7.3 |
| 4.x, BOM Hibernate ≥ 7.3 | ≥ 7.3 | Yes |

Until a supported Spring Boot release ships Hibernate ≥ 7.3, consumers override the managed version:
Gradle `ext['hibernate.version'] = '7.3.x.Final'` (with the Spring Boot / `io.spring.dependency-management`
plugin) or Maven `<hibernate.version>7.3.x.Final</hibernate.version>`. The clean resolution is to set
the minimum supported Spring Boot version to the first whose BOM ships Hibernate ≥ 7.3, after which no
override is needed.

## Module structure

Two Gradle subprojects sit alongside the root `mongo-hibernate` project:

```
mongo-hibernate/                             ← core extension (dialect, connection provider, SPI)
mongodb-hibernate-spring-boot-autoconfigure/ ← auto-configuration logic
mongodb-hibernate-spring-boot-starter/       ← zero-code dependency aggregator
```

`settings.gradle.kts` includes both subprojects.

### Convention plugins

Common build configuration lives in three precompiled script plugins in `buildSrc/src/main/kotlin/`,
applied by the relevant modules:

- `mongo-hibernate-java` — Java 17 toolchain, `withJavadocJar` / `withSourcesJar`, JUnit test logging,
  `check → spotlessApply`.
- `mongo-hibernate-integration-test` — the `integrationTest` source set, task registration, IDEA
  wiring. It declares `id("java-library")` in its own `plugins {}` block because precompiled script
  plugins resolve type-safe accessors (`sourceSets`, `configurations`) at compile time.
- `mongo-hibernate-publish` — `maven-publish` + `signing` + common POM fields (url, license,
  developers, SCM).

### `mongodb-hibernate-spring-boot-autoconfigure`

Ships no `module-info.java`; declares a stable `Automatic-Module-Name`:

```kotlin
tasks.jar { manifest { attributes("Automatic-Module-Name" to "com.mongodb.hibernate.spring.boot.autoconfigure") } }
```

The module lives in the Spring ecosystem, where every Spring Framework / Spring Boot jar is an
automatic module. A full JPMS descriptor would add friction (reflective access for
`@Bean`/`@Autowired`, an `opens` set) with no benefit, since module-path execution is not a tested
mode for a Spring Boot application. The core module remains a full JPMS module because it sits in
Hibernate's world.

Dependencies:

```kotlin
api(platform(libs.spring.boot.bom))            // BOM published as a dependencyManagement import
api(project(":"))                              // mongo-hibernate; exposes the MongoConfigurationContributor SPI
implementation(libs.spring.boot.autoconfigure) // @AutoConfiguration, @ConditionalOn*, repository config base
implementation(libs.spring.boot.persistence)   // EntityScanPackages
implementation("org.springframework.boot:spring-boot-jpa")       // JpaProperties, EntityManagerFactoryBuilder
implementation("org.springframework.boot:spring-boot-hibernate") // HibernateProperties, HibernateSettings, HibernatePropertiesCustomizer
implementation("org.springframework:spring-orm")              // LocalContainerEntityManagerFactoryBean, JpaTransactionManager
implementation("jakarta.persistence:jakarta.persistence-api") // EntityManagerFactory
compileOnly(libs.jspecify)                     // compile-time annotations only
optional(libs.spring.data.jpa)                 // repository auto-config only
optional("org.springframework:spring-webmvc")  // Open-Session-in-View only (servlet web apps)
optional("org.springframework.boot:spring-boot-mongodb") // MongoConnectionDetails / MongoClient bean for borrowing
```

The `optional` configuration holds dependencies published as `<optional>true</optional>`: declared so a
consumer depending on the artifact directly can resolve them, but not forced transitively.
`compileOnly` and `testImplementation` extend it; it is mapped to Maven optional scope via Gradle's
`AdhocComponentWithVariants.addVariantsFromConfiguration { mapToOptional() }`. In the published POM,
`api` → compile scope, `implementation` → runtime scope, and `api(platform(...))` → a
`<dependencyManagement>` BOM import.

Spring Boot 4.0 module split: `@EntityScan` / `EntityScanPackages` moved from
`org.springframework.boot.autoconfigure.domain` to `org.springframework.boot.persistence.autoconfigure`
in `spring-boot-persistence`; JPA support split into `spring-boot-jpa` (`JpaProperties`,
`EntityManagerFactoryBuilder`), `spring-boot-hibernate` (`HibernateProperties`, `HibernateSettings`,
`HibernatePropertiesCustomizer`), and `spring-boot-data-jpa` (`JpaRepositoriesAutoConfiguration`); and
MongoDB client support into `spring-boot-mongodb` (`MongoAutoConfiguration`, `MongoConnectionDetails`,
`MongoClientSettingsBuilderCustomizer`, `MongoProperties` under the `spring.mongodb.*` prefix).

Published artifact: `org.mongodb:mongodb-hibernate-spring-boot-autoconfigure`.

### `mongodb-hibernate-spring-boot-starter`

No source code; a `build.gradle.kts`:

```kotlin
dependencies {
    api(project(":"))                                              // mongo-hibernate
    api(project(":mongodb-hibernate-spring-boot-autoconfigure"))
    api("org.springframework.boot:spring-boot-data-jpa")           // Spring Data JPA + Hibernate ORM, no SQL pool
    api("org.springframework.boot:spring-boot-mongodb")            // MongoClient infrastructure to borrow
}
```

Maven optional is not transitive, so the starter supplies Spring Data JPA and `spring-boot-mongodb`
itself, making repository support and the borrowable `MongoClient` available by default for starter
users while keeping them opt-in for direct autoconfigure consumers.

The starter depends on the granular `spring-boot-data-jpa` module, **not** `spring-boot-starter-data-jpa`.
The latter also pulls in `spring-boot-starter-jdbc` → HikariCP, a SQL connection pool a MongoDB-backed
application does not want; with a pool on the classpath but no `spring.datasource.url`,
`DataSourceAutoConfiguration` fails fast at startup (see [Auto-configuration ordering](#auto-configuration-ordering)).
The starter also omits the base `spring-boot-starter`: a feature starter should not impose a logging
backend or other application foundation — the application's primary starter (`spring-boot-starter-web`, or
plain `spring-boot-starter`) provides that. A `check`-wired Gradle task fails the build if a SQL connection
pool ever reappears on the starter's runtime classpath. Published artifact:
`org.mongodb:mongodb-hibernate-spring-boot-starter`.

## Prior art: how Spring Boot shares one `DataSource`

A `MongoClient` is a connection pool, the MongoDB analogue of a JDBC `DataSource`. Spring Boot's SQL
stack has an established pattern for sharing one pool between JPA and other consumers (raw JDBC, health,
metrics), which this design follows.

The SQL stack treats the pool as a shared bean that JPA borrows:

- `DataSourceAutoConfiguration`'s pooled-`DataSource` creation is
  `@ConditionalOnMissingBean({ DataSource.class, XADataSource.class })`. If the application supplies a
  `DataSource` bean, Boot's creation backs off. `spring.datasource.url` is the recipe for the
  auto-created pool only.
- `JpaBaseConfiguration` receives the `DataSource` in its constructor and passes it to the
  `EntityManagerFactory` (`factoryBuilder.dataSource(this.dataSource)…`). JPA does not create a pool or
  re-specify connection coordinates.
- `HibernateJpaConfiguration` is `@ConditionalOnSingleCandidate(DataSource.class)`; it binds to the
  single pool that exists.
- The pool's lifecycle is Spring's: Hibernate borrows connections and does not close the `DataSource`.
- JPA intent comes from the JPA classes on the classpath (`HibernateJpaAutoConfiguration` is
  `@ConditionalOnClass({ LocalContainerEntityManagerFactoryBean, EntityManager, SessionImplementor })`),
  not from the `DataSource` bean. A `DataSource` is also used for raw JDBC, Batch, Quartz, and Flyway
  with no JPA. `@ConditionalOnSingleCandidate(DataSource.class)` is a pool-availability gate, not an
  intent gate.

Two points from this model inform the design:

- Duplicate pools are prevented structurally, not by configuration discipline. The SQL stack does this
  by backing off auto-creation when a pool bean exists
  (`@ConditionalOnMissingBean(DataSource.class)`), with the URL as the fallback recipe. This design has
  no URL fallback in the Spring path: it borrows an existing `MongoClient` and fails fast if none is
  present (see [Pool: borrow-only](#pool-borrow-only)).
- The pool bean is not the activation signal. Activation is a separate concern. In the SQL stack the
  signal is the JPA classes on the classpath. For MongoDB the classpath is insufficient, because the
  auto-configure jar can be present on a SQL-JPA application's classpath; JPA-on-classpath does not
  distinguish MongoDB JPA from SQL JPA. The design uses an explicit property,
  `spring.jpa.database-platform=MongoDB` (see [Activation model](#activation-model)). The carried-over
  point is that the `MongoClient` bean is not the trigger.

## Where the `DataSource` analogy does not hold

Two concerns are not covered by the borrowed pool: dialect and database name.

- Dialect. When neither `spring.jpa.database-platform` nor `spring.jpa.database` is set, Hibernate
  determines the dialect by opening a JDBC connection and reading `java.sql.DatabaseMetaData` (product
  name and version). A `MongoClient` cannot be read this way, so the dialect must be named explicitly.
  `spring.jpa.database` is a closed, SQL-only enum (`H2`, `MYSQL`, `POSTGRESQL`, …; no `MONGODB`, not
  extensible). `spring.jpa.database-platform` is a free-form dialect string that maps to
  `hibernate.dialect` and takes precedence over the enum, and is the mechanism used here.
- Database name. A JDBC connection is database-scoped (the catalog is in the URL, e.g.
  `jdbc:postgresql://host/mydb`), so Hibernate inherits it. A `MongoClient` is cluster-scoped; the
  database is chosen per operation. The database name comes from an explicit source (see
  [Database name](#database-name)).

## User-facing configuration

```properties
spring.jpa.database-platform=MongoDB
spring.mongodb.uri=mongodb://localhost/mydb
```

`spring.jpa.database-platform=MongoDB` activates the integration and selects the dialect.
`spring.mongodb.*` configures the `MongoClient` that `spring-boot-mongodb` builds and the integration
borrows. The full `spring.jpa.*` surface applies as in any Spring Boot JPA application.

Client customization uses Spring's `MongoClientSettingsBuilderCustomizer`, which `spring-boot-mongodb`
applies when it builds the `MongoClient`:

```java
@Bean
MongoClientSettingsBuilderCustomizer mongoCustomizer() {
    return builder -> builder.addCommandListener(myListener);
}
```

The integration borrows the already-built client, so `MongoConfigurationContributor.applyToMongoClientSettings(...)`
has no effect on the Spring path (it remains the customization mechanism for the standalone/manual
path). `MongoConfigurationContributor.databaseName(...)` is honored on either path.

## Activation model

The trigger is `spring.jpa.database-platform=MongoDB`.

There is no URL-based activation in the Spring layer; `spring.datasource.url=mongodb://…` is not a
Spring activation path.

- JPA intent comes from the JPA classes on the classpath, as in the SQL stack.
- The MongoDB-specific need is a database-flavor signal. The auto-configure jar can be present on a
  SQL-JPA application's classpath (transitively, shared parent POM, multi-module build), where
  JPA-on-classpath is true but the application uses SQL. `spring.jpa.database-platform=MongoDB`
  identifies the JPA platform as MongoDB.
- A `MongoClient` bean is not a trigger. A `MongoClient` is the normal state of any Spring + MongoDB
  application; it is usually present for `MongoTemplate` / Spring Data MongoDB rather than Hibernate, so
  its presence does not indicate Hibernate-JPA intent. Triggering on it would activate in ordinary
  Spring Data MongoDB applications. An explicit property also keeps activation controllable (toggle a
  property rather than restructure dependencies, which supports staged `MongoTemplate` → Hibernate
  migration) and reuses the dialect declaration. `@ConditionalOnSingleCandidate(MongoClient.class)` is
  not used (see [Rejected alternatives](#rejected-alternatives)).

The condition matches the literal string `MongoDB`, the dialect short name (see [Dialect](#dialect)).
The extension supports only that short name; no `MongoDialect` FQCN or test-dialect accommodation is
required.

`spring.jpa.database-platform=MongoDB` serves three purposes through existing machinery:

1. Dialect selection: the module's `jpaVendorAdapter` bean applies it via `setDatabasePlatform`,
   yielding `hibernate.dialect=MongoDB`.
2. Connection-provider selection: the core treats `MongoDB` as a Mongo dialect and selects
   `MongoConnectionProvider`.
3. Activation trigger.

The condition is a `SpringBootCondition` reading `spring.jpa.database-platform` from the environment. It
gates both `MongoHibernateAutoConfiguration` and `MongoJpaRepositoriesAutoConfiguration`.

## `MongoHibernateAutoConfiguration`

Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
Activates when mongo-hibernate is on the classpath
(`@ConditionalOnClass(MongoConfigurationContributor.class)` — the exported SPI class, not the internal
`MongoDialect`) and the activation condition matches.

Rather than hand-assembling the `EntityManagerFactory`, the configuration reuses Spring Boot's JPA
property machinery — `JpaProperties`, `HibernateProperties`, `EntityManagerFactoryBuilder` — so the
full `spring.jpa.*` surface applies: `ddl-auto`, `show-sql`, `generate-ddl`, database/platform, naming
strategies, and `HibernatePropertiesCustomizer` / `EntityManagerFactoryBuilderCustomizer` beans. Spring
Boot's `HibernateJpaAutoConfiguration` cannot be reused directly: it is
`@ConditionalOnSingleCandidate(DataSource.class)` and its constructor and ddl-auto defaulting require a
non-null `DataSource`. The property-merge classes are public and DataSource-free, so the configuration
replicates the small `getVendorProperties` flow with a MongoDB-appropriate ddl-auto default and reuses
the rest. `@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class})` binds the
configuration.

Back-off is per bean, not class-wide: each bean carries `@ConditionalOnMissingBean`, so a user-supplied
`EntityManagerFactory` suppresses only the `EntityManagerFactory` bean rather than disabling the whole
configuration.

Beans:

- `JpaVendorAdapter` — a `HibernateJpaVendorAdapter` with `show-sql`, `database`, `database-platform`,
  and `generate-ddl` applied from `JpaProperties`, as `JpaBaseConfiguration.jpaVendorAdapter` does.
  `database-platform` is `MongoDB`, which produces `hibernate.dialect=MongoDB`.
- `EntityManagerFactoryBuilder` — constructed with a JPA-properties factory that ignores its
  `DataSource` argument and returns the assembled Hibernate property map;
  `EntityManagerFactoryBuilderCustomizer` beans are applied in `@Order` order.
- `LocalContainerEntityManagerFactoryBean` (`@Primary`,
  `@ConditionalOnMissingBean({LocalContainerEntityManagerFactoryBean.class, EntityManagerFactory.class})`)
  — built via `factoryBuilder.dataSource(null)…build()`. No `DataSource`; the borrowed client is passed
  through the `MongoConfigurationContributor` bridge (see [Core module change](#core-module-change)).
  `@Primary` matches Spring Boot and avoids ambiguity when a second `EntityManagerFactory` is present
  (single persistence unit is the auto-configured model; see [Multiple persistence units](#multiple-persistence-units)).
  Packages to scan come from `EntityScanPackages` (populated by `@EntityScan`), falling back to
  `AutoConfigurationPackages`.
- `JpaTransactionManager` (`@ConditionalOnMissingBean(TransactionManager.class)`) — wired to the
  `EntityManagerFactory`.

The Hibernate property map is `HibernateProperties.determineHibernateProperties(base, settings)`, with
`settings` carrying a fixed `ddlAuto(() -> "none")` default (MongoDB has no embedded-database notion, so
Spring Boot's `DataSource`-dependent `HibernateDefaultDdlAutoProvider` is not used) plus the ordered
`HibernatePropertiesCustomizer` beans.

### Naming strategy

Spring Boot's `HibernateProperties` defaults the physical naming strategy to
`PhysicalNamingStrategySnakeCaseImpl` (camelCase → snake_case). This configuration keeps Hibernate's
own default (`PhysicalNamingStrategyStandardImpl`, the Java property name). snake_case is safe on a
relational store because the schema is a guardrail and a mismatch errors. MongoDB is schemaless, so
writes under two naming strategies coexist silently in one collection (old documents `firstName`, new
documents `first_name`, reads see `null`). The core extension sets no naming strategy (plain Hibernate
→ camelCase), so matching that here means an entity persists identically whether bootstrapped via the
core extension or via this starter. The implicit naming strategy is likewise kept at Hibernate's default
(`ImplicitNamingStrategyJpaCompliantImpl`) rather than Spring Boot's `SpringImplicitNamingStrategy`.

Implemented by seeding Hibernate's default strategy class names with `putIfAbsent` before
`determineHibernateProperties`. An explicit `spring.jpa.hibernate.naming.physical-strategy` /
`implicit-strategy` (which Spring Boot applies with `put`) or a raw
`spring.jpa.properties.hibernate.*_naming_strategy` still wins; only the unset case falls back to the
Hibernate default. Detection keys off whether the property is set, not its value, so a user requesting
snake_case explicitly gets it.

### Open Session in View

An inner `@Configuration` mirrors Spring Boot's `JpaWebConfiguration`:
`@ConditionalOnWebApplication(type = SERVLET)`, `@ConditionalOnClass(WebMvcConfigurer)`,
`@ConditionalOnMissingBean(OpenEntityManagerInViewInterceptor.class)`,
`@ConditionalOnBooleanProperty(name = "spring.jpa.open-in-view", matchIfMissing = true)`. It registers
an `OpenEntityManagerInViewInterceptor` (with Spring Boot's default-on warning when
`spring.jpa.open-in-view` is unset) and a `WebMvcConfigurer` that adds it. `spring-webmvc` is an
optional dependency; OSIV applies only to servlet web applications.

### Native image

An `@ImportRuntimeHints` registrar mirrors Spring Boot's `HibernateRuntimeHints` for the naming-strategy
types and the `NoJtaPlatform` types Hibernate instantiates reflectively, so the configuration works in a
native image.

## Pool: borrow-only

Once activated, the integration borrows an existing `MongoClient` bean and does not create one in the
Spring path.

- The client normally comes from `spring-boot-mongodb`'s `MongoAutoConfiguration`, configured through
  the `spring.mongodb.*` namespace. (The `spring.data.mongodb.*` keys carry an error-level deprecation
  `since: 4.0.0` with `replacement: spring.mongodb.*` in `spring-boot-mongodb`'s configuration metadata;
  they no longer bind.) The client may instead be a user-supplied `MongoClient @Bean`, or one built from
  a custom `MongoConnectionDetails` (Testcontainers `@ServiceConnection`, Docker Compose, cloud service
  binding).
- Spring's `MongoClient` is not suppressed. It backs health indicators, Micrometer metrics, and
  `MongoTemplate`; suppressing it would break those. When one exists, it is shared, not replaced.
- No second pool is created. This mirrors `@ConditionalOnMissingBean(DataSource.class)`: an existing
  client bean wins.
- If activated with no `MongoClient` bean to borrow (e.g. `spring-boot-mongodb` absent and no user
  `MongoClient @Bean`), startup fails with an actionable message: a MongoDB JPA platform is configured
  but no `MongoClient` bean is available; add `spring-boot-mongodb` (configure `spring.mongodb.*`),
  define a `MongoClient @Bean`, or use the manual path. Without this check, bootstrap fails later in
  Hibernate with a `jakarta.persistence.jdbc.url` is required error.

### Borrowing a Spring-built client is correctness-safe

Everything that configures a Spring `MongoClient` flows through ordered
`MongoClientSettingsBuilderCustomizer` beans applied by `MongoClientFactory` over an empty base
`MongoClientSettings`:

- `StandardMongoClientSettingsBuilderCustomizer` applies the connection string (from
  `MongoConnectionDetails`), UUID representation, and SSL.
- `MongoMetricsAutoConfiguration` contributes the Micrometer command- and connection-pool-listener
  customizers.
- User `MongoClientSettingsBuilderCustomizer` beans add listeners, application name, and similar.

A borrowed client already carries coordinates, metrics, and user customizations, so the integration
does not collect customizers itself. The extension injects no mandatory client-level settings: it
operates at the `BsonDocument` level (`mongoClient.getDatabase(name).getCollection(name,
BsonDocument.class)`) using the driver's default codec registry, and `MongoConfigurationBuilder` applies
only `MongoClientSettings.builder()` plus the connection string. A Spring-built client has what the
extension requires.

## Lifecycle / ownership

The component that supplies the client decides whether to close it:

- Borrowed (Spring path): `MongoConnectionProvider` does not close the client; Spring owns its
  lifecycle.
- Self-created (standalone path, unchanged): `MongoConnectionProvider` closes it in `stop()`.

This is expressed in the core SPI (see [Core module change](#core-module-change)).

## Dialect

Spring Boot binds `spring.jpa.database-platform` to `JpaProperties`; it does nothing further on this
path, because its own `HibernateJpaAutoConfiguration` (which would build a vendor adapter and apply the
property) is off without a `DataSource`. The module's `jpaVendorAdapter` bean reads
`JpaProperties.getDatabasePlatform()` and calls `HibernateJpaVendorAdapter.setDatabasePlatform("MongoDB")`;
the adapter's JPA property map then carries `hibernate.dialect=MongoDB` into the `EntityManagerFactory`
properties. Hibernate resolves `MongoDB` → `MongoDialect` through the core module's
`MongoNamedStrategyContributor`, registered via Hibernate's `NamedStrategyContributor` SPI
(`META-INF/services/org.hibernate.boot.registry.selector.spi.NamedStrategyContributor`).

The `jpaVendorAdapter` bean already applies `database-platform` (it honors the full `spring.jpa.*`
surface), so no new dialect wiring is needed; resolution is the same as for standalone use.

## Database name

A `MongoClient` is not database-scoped, so the database name needs an explicit source. The precedence
mirrors Spring's own `MongoDatabaseFactoryConfiguration` (which builds the `MongoDatabaseFactory`
behind `MongoTemplate`): it reads `MongoProperties.getDatabase()` (`spring.mongodb.database`) first,
falls back to `connectionDetails.getConnectionString().getDatabase()` when that is null, then asserts
the result is non-empty. Reading `getDatabase()` directly (not relying on the connection string) is
necessary because `PropertiesMongoConnectionDetails.getConnectionString()` folds the database in only
for the host/properties form; its URI branch returns the raw URI and ignores `spring.mongodb.database`.
Using the same order keeps Hibernate on the same database the rest of the Spring MongoDB stack targets.
In order:

1. `MongoConfigurationContributor.databaseName(…)` — an explicit override. It is applied last in the
   contributor composite, and the autoconfigure's database-name contributor is ordered
   `@Order(HIGHEST_PRECEDENCE)`, so a user contributor wins.
2. `spring.mongodb.database`, read from the `MongoProperties` bean (`getDatabase()`), when set. It
   overrides a database embedded in `spring.mongodb.uri`, as `MongoDatabaseFactoryConfiguration` does.
   `MongoProperties` is always present on the borrow path (registered by `MongoAutoConfiguration`
   whenever `spring-boot-mongodb` is on the classpath), so reading it is not fragile.
3. `MongoConnectionDetails.getConnectionString().getDatabase()` — the connection string's database.
   This is the source-agnostic step: it covers a URI with a database path
   (`spring.mongodb.uri=mongodb://host/db`), the `host` + `database` form, and custom
   `MongoConnectionDetails` sources (Testcontainers and similar) that have no `MongoProperties` behind
   them. It yields `test` for the bare default (`MongoProperties.DEFAULT_URI` is
   `mongodb://localhost/test`), which is honored rather than treated as an error.
4. Otherwise, fail with an actionable message: put the database in the URI (`mongodb://host/<db>`), set
   `spring.mongodb.database`, or set `MongoConfigurationContributor.databaseName(…)`.

## `MongoJpaRepositoriesAutoConfiguration`

Spring Boot's `JpaRepositoriesAutoConfiguration` activates `@EnableJpaRepositories` but carries
`@ConditionalOnBean(DataSource.class)`, so it never fires without a SQL `DataSource`.
`MongoJpaRepositoriesAutoConfiguration` is a replacement that drops the `DataSource` requirement. It
activates when `JpaRepository` is on the classpath (`@ConditionalOnClass(JpaRepository.class)`), the
activation condition matches, no `JpaRepositoryFactoryBean` / `JpaRepositoryConfigExtension` bean
exists (`@ConditionalOnMissingBean`), and
`@ConditionalOnBooleanProperty(name = "spring.data.jpa.repositories.enabled", matchIfMissing = true)`.

It uses `AbstractRepositoryConfigurationSourceSupport` (the Spring Boot base class for repository
auto-configurations) to wire `@EnableJpaRepositories` with `AutoConfigurationPackages` as the scan base,
so `@EntityScan` and the main application package work without extra configuration. A user who declares
`@EnableJpaRepositories` takes precedence and this configuration backs off.

It is ordered `@AutoConfiguration(after = MongoHibernateAutoConfiguration.class)` and `beforeName`
Spring Boot's `JpaRepositoriesAutoConfiguration` (see [Auto-configuration ordering](#auto-configuration-ordering)).

## Core module change

The Spring layer cannot inject a pre-built `MongoClient` into Hibernate today;
`MongoConnectionProvider` always calls `MongoClients.create(...)`. The core gains the ability to accept
a supplied client and to not close it. This is the only core change, and it also serves the manual path
(with or without Spring).

1. `MongoConfigurator` (public SPI): add `mongoClient(MongoClient)`. Supplying a client means use this
   client and do not close it. The API is flat — no compile-time type-state separating it from
   `applyToMongoClientSettings`. The conflict it would guard against is across separate contributor
   beans, which a type-state cannot catch, and the resolution is that the supplied client wins.
2. `MongoConfiguration` (record): carry an optional external `MongoClient` alongside
   `mongoClientSettings` and `databaseName`.
3. `MongoConfigurationBuilder`: store the optional client. When present, the connection string and
   `applyToMongoClientSettings` are not used to build a connection (the supplied pool wins; the URL is
   ignored).
4. `MongoConnectionProvider`: if an external client is present, use it and do not call `close()` in
   `stop()`; otherwise create and own the client as today.

The existing JPA-properties bridge passes the client across the module boundary: the autoconfigure
places a `MongoConfigurationContributor` under the configuration key
`com.mongodb.hibernate.configurationContributor`, which the core consults before its service-registry
lookup. No cross-module surface is added beyond the `mongoClient(...)` method.

The autoconfigure's share bridge bean is gated by `@ConditionalOnClass(MongoConnectionDetails.class)` +
`@ConditionalOnBean(MongoClient.class)` + the activation condition. It registers a
`MongoConfigurationContributor` (ordered `@Order(HIGHEST_PRECEDENCE)`) that calls
`configurator.mongoClient(theBean)` and sets the database name resolved per
[Database name](#database-name) (`spring.mongodb.database` if set, otherwise the `MongoConnectionDetails`
connection string). The bridge depends on the `MongoConnectionDetails` and `MongoProperties` beans.

## Auto-configuration ordering

This design uses no `EnvironmentPostProcessor`. A URL-based approach might use one to copy
`spring.datasource.url` into `spring.jpa.properties.jakarta.persistence.jdbc.url` and to exclude
`DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration` via `spring.autoconfigure.exclude`.
Neither is needed:

- There is no `spring.datasource.url` in the Spring path; the dialect comes from
  `spring.jpa.database-platform=MongoDB`.
- The exclusion existed because, with `spring.datasource.url=mongodb://…`,
  `DataSourceAutoConfiguration` would attempt and fail to build a SQL connection pool from a Mongo URL.

There is a second failure mode the exclusion also masked: when a pooled `DataSource` implementation
(HikariCP) is on the classpath but no `spring.datasource.url` is set, `DataSourceAutoConfiguration`
fails fast with "'url' attribute is not specified." Rather than re-introduce an exclusion (which would
break a user who legitimately wants a SQL `DataSource`, see below), the starter avoids the trigger: it
depends on the granular `spring-boot-data-jpa` module instead of `spring-boot-starter-data-jpa`, so it
does **not** drag in `spring-boot-starter-jdbc` → HikariCP. With no pooled `DataSource` on the classpath
and no `url`, `DataSourceAutoConfiguration` finds nothing to do and creates nothing — no failure. A user
who genuinely wants SQL alongside MongoDB adds HikariCP and a `url` themselves, and Spring behaves
normally.

The remaining concern is a stray `DataSource` — an embedded database (e.g. H2 on the test classpath) or
a non-JPA `DataSource` (for `JdbcTemplate`, Flyway, Batch) — causing Spring's
`HibernateJpaAutoConfiguration` (`@ConditionalOnSingleCandidate(DataSource.class)`) to build a competing
SQL `EntityManagerFactory`. The `entityManagerFactory` and `transactionManager` beans here are
`@Primary` and back off if Spring's are registered first
(`@ConditionalOnMissingBean({LocalContainerEntityManagerFactoryBean.class, EntityManagerFactory.class})`).
Precedence is handled by ordering: `MongoHibernateAutoConfiguration` is declared
`@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)`, so the `@Primary` Mongo
`EntityManagerFactory` and `JpaTransactionManager` register first and Spring's SQL-JPA beans back off
through their own `@ConditionalOnMissingBean`. `MongoJpaRepositoriesAutoConfiguration` is ordered
`beforeName` Spring Boot's `JpaRepositoriesAutoConfiguration` for the same reason (a string reference,
since Spring Data JPA is an `optional` dependency).

`DataSourceAutoConfiguration` is not excluded: a MongoDB-JPA application may use a SQL `DataSource` for
non-JPA purposes (`JdbcTemplate`, Flyway, Batch), and excluding it would break that. Because the starter
brings no connection pool (above), a pure-MongoDB application never triggers it; the only consequence is
that an idle embedded `DataSource` may be created when an embedded-database driver is on the classpath
(e.g. H2 on the test classpath), which is Spring Boot's documented behavior, and nothing binds to it.

## Manual path

The auto-configuration covers the single-persistence-unit case. Applications that want a dedicated
client, multiple persistence units (see [below](#multiple-persistence-units)), or other custom wiring
configure JPA manually — outside Spring, or with Spring using hand-written JPA `@Configuration`. In both
cases the application builds the Mongo persistence unit and supplies the client through
`MongoConfigurator.mongoClient(...)` (owned or closed per the provider's ownership rule). The starter is
the share-the-pool turnkey path on top of that SPI.

## Multiple persistence units

Running MongoDB and a SQL database (e.g. Postgres) in one application, each with its own
`EntityManagerFactory`, is supported through the standard Spring manual multi-persistence-unit pattern,
not through auto-configuration.

Two auto-configured `EntityManagerFactory` beans are not possible, and this is a Spring Boot trait
rather than a limitation of this extension: Spring's `HibernateJpaConfiguration` is
`@ConditionalOnSingleCandidate(DataSource.class)` and its EMF bean is
`@ConditionalOnMissingBean({LocalContainerEntityManagerFactoryBean.class, EntityManagerFactory.class})`
(this module's EMF carries the same back-off), so auto-configured EMFs are mutually exclusive: the first
registered wins and the rest back off. The `spring.jpa.*` surface, including `database-platform`, is a
single global setting feeding one vendor adapter, and is single-PU.

The multi-PU setup is the conventional one: the application defines both persistence units by hand —
two `LocalContainerEntityManagerFactoryBean`s, two transaction managers, `@EnableJpaRepositories` per
unit with explicit `entityManagerFactoryRef` / `transactionManagerRef` / `basePackages`, one marked
`@Primary`. In that configuration:

- This module's auto-configuration stays inert: a multi-PU application does not set the global
  `spring.jpa.database-platform=MongoDB` (each unit names its dialect locally), so the activation
  condition does not match. Nothing needs to be excluded.
- The MongoDB unit is built with `MongoDialect`, `MongoConnectionProvider`, and, to share a
  Spring-managed client, `MongoConfigurator.mongoClient(...)`.
- Core-level coexistence already holds: this module's global Hibernate `ServiceContributor` and
  `MongoConnectionProvider` engage only when the dialect is `MongoDB`, so a SQL `SessionFactory` is
  untouched. This is covered by the existing negative-coexistence integration test (a SQL application
  with this module on its classpath boots and round-trips).

## Not in scope

- Suppressing Spring's `MongoClient` bean.
- Collecting `MongoClientSettingsBuilderCustomizer` beans; they are already applied to the borrowed
  client by Spring.
- Compile-time type-state separating `mongoClient()` from `applyToMongoClientSettings()`.
- URL-based activation in the Spring layer. Standalone, non-Spring Hibernate retains its URL-driven,
  self-created-client behavior as one of the manual paths.

## Rejected alternatives

### Zero-config activation on `MongoClient` presence

Activate whenever a single `MongoClient` bean is present —
`@ConditionalOnSingleCandidate(MongoClient.class)` plus JPA classes and `spring-boot-mongodb` on the
classpath — and default `hibernate.dialect` to `MongoDB` in the borrow path. The application would add
the starter, configure `spring.mongodb.*`, write `@Entity` classes, and the integration would activate
with no MongoDB-specific property.

Arguments for: it is the turnkey experience, analogous to `spring-boot-starter-data-mongodb`. It treats
presence of the deliberately-added integration module as the opt-in, which is reasonable for a leaf
integration starter that is not a typical transitive dependency. It avoids requiring the
`spring.jpa.database-platform` property, which is real and non-deprecated but documented only in Spring
Boot's generated "Common Application Properties" appendix.

Reasons rejected:

- `MongoClient` presence is a weak signal. Unlike a `DataSource`, a `MongoClient` bean is the normal
  state of any Spring + MongoDB application; it is usually present for `MongoTemplate` / Spring Data
  MongoDB. Triggering on it would activate in Spring Data MongoDB applications that do not use Hibernate,
  registering a `@Primary` `EntityManagerFactory` and `JpaTransactionManager` that scan `@Entity`
  classes.
- Controllability. A property can be toggled; a dependency cannot, without restructuring or explicit
  excludes. An application migrating from `MongoTemplate` to Hibernate-JPA over the same database can add
  the dependency first and enable the JPA layer later by setting the property; presence-based activation
  would enable everything immediately.
- The explicit signal is low-cost. The dialect must be named somewhere regardless: a borrowed
  `MongoClient` cannot be read for it (unlike a JDBC `DataSource`), and the `spring.jpa.database` enum
  has no `MongoDB` value. `spring.jpa.database-platform=MongoDB` is where the dialect is named, so
  requiring it is not an additional step.
- Spring Boot's gating convention is to not activate on a bean that commonly exists for other reasons.

The decision is close; the discoverability cost is accepted in exchange for not activating on a signal
(`MongoClient` presence) that does not indicate Hibernate-JPA over MongoDB. Discoverability is handled
with getting-started documentation.

### URL-based activation with a self-created client

Activate on `spring.datasource.url=mongodb://…`, copy it to `jakarta.persistence.jdbc.url` through an
`EnvironmentPostProcessor`, and have `MongoConnectionProvider` build and own a `MongoClient` from it.

Reasons rejected:

- Duplicate connection pools. Spring-native client customization requires `spring-boot-mongodb`, whose
  `MongoAutoConfiguration` creates a `MongoClient` bean (also used for health checks, Micrometer metrics,
  and Spring Data MongoDB); a separately URL-built client is a second pool to the same cluster.
- `spring.datasource.url` implies a SQL `DataSource`, which is not created for MongoDB.

## Testing

Per the module's conventions: `ApplicationContextRunner` / `WebApplicationContextRunner` for
auto-configuration unit tests (no MongoDB), `@SpringBootTest` for integration tests (MongoDB required).
Auto-configurations are registered with the runner via
`withConfiguration(AutoConfigurations.of(...))`, not `withUserConfiguration(...)`, so
`@ConditionalOnMissingBean` orders reliably (auto-configurations are evaluated after user beans).

### Unit (no MongoDB)

- Activation gate: the auto-configurations activate when `spring.jpa.database-platform=MongoDB` and stay
  inert otherwise, including when a `MongoClient` bean is present but the property is unset, and for a
  non-MongoDB platform.
- Share bridge present/absent: with `spring-boot-mongodb` on the classpath and a `MongoClient` bean, the
  bridge `MongoConfigurationContributor` is registered; with `MongoConnectionDetails` absent
  (`FilteredClassLoader`), it is not.
- Database-name resolution: `spring.mongodb.database` overrides a URI-embedded database (matching
  `getMongoClientDatabase()`); otherwise the connection string's database is used (URI-with-db,
  `host`+`database`, custom `MongoConnectionDetails`); fails when no source provides a database; a user
  `databaseName(...)` contributor overrides all.
- Activated but no client: with `spring.jpa.database-platform=MongoDB` set and no `MongoClient` bean,
  startup fails with the "no `MongoClient` bean" message rather than the Hibernate
  `jakarta.persistence.jdbc.url` error.
- Property assembly: `spring.jpa.hibernate.ddl-auto` produces `hibernate.hbm2ddl.auto`; the physical and
  implicit naming strategies default to Hibernate's own (identity/camelCase and JPA-compliant) when unset
  and honor explicit `spring.jpa.hibernate.naming.physical-strategy` / `implicit-strategy` overrides; a
  `HibernatePropertiesCustomizer` bean's mutation appears; the contributor composite lands under the
  bridge key. (Exercised via the
  `EntityManagerFactoryBuilder`'s `getJpaPropertyMap()` without bootstrapping Hibernate, with a mock
  `EntityManagerFactory` bean making the EMF bean back off.)
- Open Session in View: present by default in a servlet web context, absent when
  `spring.jpa.open-in-view=false`.
- Repository back-off: a user `JpaRepositoryFactoryBean` suppresses repository scanning; with
  `JpaRepository` absent (`FilteredClassLoader`) no repository infrastructure is created.
- SQL-JPA precedence (ordering, not exclusion): with `spring.jpa.database-platform=MongoDB`, a
  `MongoClient` bean, and a `DataSource` bean present (a stray/embedded `DataSource`), the context has
  one `EntityManagerFactory` — the `@Primary` Mongo one — and Spring's `HibernateJpaAutoConfiguration`
  EMF and `JpaTransactionManager` back off.

### Core (no MongoDB)

- `MongoConnectionProvider` borrow path: given a `MongoConfiguration` carrying an external client,
  `getConnection()` uses it and `stop()` does not close it; given none, it creates and closes its own.
- `MongoConfigurationBuilder`: a supplied client wins over connection-string settings.

### Integration (MongoDB)

- Share, end to end: with `spring-boot-mongodb` configuring a `MongoClient` and
  `spring.jpa.database-platform=MongoDB`, an entity round-trips, and entity-manager operations issue
  their commands through the Spring-managed `MongoClient` — observed via a `CommandListener` registered
  on it — proving the borrow.
- Transactions: a rolled-back transaction persists nothing.
- Database name: `spring.mongodb.database` overrides the database embedded in `spring.mongodb.uri`, with
  the document landing in the overriding database (verified against the raw driver).
- Repository: a `JpaRepository` bean is created and a find returns results.
- Naming: a multi-word field persists with a camelCase document key by default, and snake_case when
  `spring.jpa.hibernate.naming.physical-strategy` is set to the snake_case strategy (asserted against
  the raw BSON document).
- Entity scanning: an entity in a separate package referenced via `@EntityScan` is discovered.
- Negative coexistence: a SQL JPA application (in-memory H2, no MongoDB) with this module on its
  classpath boots and round-trips.
- Manual path: a contributor supplying its own `MongoClient` via `mongoClient(...)` is used, and
  `MongoConnectionProvider` closes it (owned path), exercised via the core/standalone path.

## Limitations and follow-ups

- `MongoClient` present but `spring.jpa.database-platform` unset: the integration is inert (`MongoClient`
  is lazy, so there is no startup connection attempt). Consider a `FailureAnalyzer` or documentation
  note: a `MongoClient` is present but `spring.jpa.database-platform=MongoDB` is not set.
- Documentation: the README and the external test application are updated to the activation model
  (`spring.mongodb.*` + `spring.jpa.database-platform=MongoDB`, plus the starter or `spring-boot-mongodb`
  dependency). A note states that the integration reuses `spring-boot-mongodb`'s client infrastructure
  and is not Spring Data MongoDB.
- `@DataJpaTest` slice is unsupported. The slice imports a fixed set of auto-configurations
  (`HibernateJpaAutoConfiguration`, `DataSourceAutoConfiguration`) and replaces the `DataSource` with an
  embedded SQL database, none of which match this integration. Full slice support would need a dedicated
  test-slice annotation with its own `TypeExcludeFilter` and `@ImportAutoConfiguration` set. Tracked as
  HIBERNATE-179.

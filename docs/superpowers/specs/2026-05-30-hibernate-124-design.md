# HIBERNATE-124: Register Short Names and Auto-Configure Connection Provider

## Goal

Simplify MongoDB extension configuration to two properties:

```properties
hibernate.dialect=MongoDB
jakarta.persistence.jdbc.url=mongodb://...
```

Previously users had to supply three properties using extension class names:

```properties
hibernate.dialect=com.mongodb.hibernate.dialect.MongoDialect
hibernate.connection.provider_class=com.mongodb.hibernate.jdbc.MongoConnectionProvider
jakarta.persistence.jdbc.url=mongodb://...
```

## Package Internalization

Move `com.mongodb.hibernate.dialect` and `com.mongodb.hibernate.jdbc` to
`com.mongodb.hibernate.internal.dialect` and `com.mongodb.hibernate.internal.jdbc`,
removing them from the public API surface. The classes previously documented
configuration and BSON type mappings; since they are now internal, that
documentation moves to module-level Javadoc in `module-info.java`.
Corresponding `doclint` exclusions in `build.gradle.kts` are removed.

## Dialect Short Name

Register `"MongoDB"` as a short name for `MongoDialect` via a new
`MongoNamedStrategyContributor` implementing Hibernate's `NamedStrategyContributor`
SPI. This follows Hibernate ORM naming conventions (PascalCase for dialects,
e.g. `PostgreSQL`, `MySQL`).

## Connection Provider Auto-Configuration

`MongoConnectionProvider` cannot be registered as a named strategy because
`ConnectionProviderInitiator` would auto-select a singly-registered
`ConnectionProvider` for every session, hijacking non-MongoDB sessions sharing
the same `BootstrapServiceRegistry`. Instead, `ServiceContributor.contribute()`
detects the MongoDB dialect and sets `CONNECTION_PROVIDER` to the class name
directly, so `ConnectionProviderInitiator` resolves it via class loading,
bypassing the strategy registry.

If `CONNECTION_PROVIDER` is already set to an incompatible value, `contribute()`
throws immediately with a helpful message. Because `contribute()` guarantees
the correct state, `checkMongoConnectionProviderIsPluggedIn` is no longer needed
and is removed.

## Dialect Validation

`checkMongoDialectIsPluggedIn` is tightened to require exactly the `"MongoDB"`
short name. `MongoDialect` itself is rejected to enforce the short name for
production use. The check is widened to also accept `TestMongoDialect`
subclasses, which integration tests use as custom dialect implementations.

**Why `TestMongoDialect` is a safe boundary:** `MongoDialect` is `sealed`,
permitting only `TestMongoDialect`. `TestMongoDialect` is `non-sealed` but
the `com.mongodb.hibernate.internal.dialect` package is not exported by the
module, so external code cannot subclass either class on the module path.

An `isMongoDialect()` helper is extracted and shared between `contribute()`
(for auto-configuration) and `checkMongoDialectIsPluggedIn()` (for validation).

As before, the initiator is registered unconditionally so that 
`checkMongoDialectIsPluggedIn` provides a helpful error whenever the service 
is requested from a misconfigured session.

## Test Infrastructure

Hibernate's testing framework (`ServiceRegistryUtil.serviceRegistryBuilder`)
injects `CONNECTION_PROVIDER=SharedDriverManagerConnectionProvider` and
`CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT=true` into the builder before
`ServiceContributor.contribute()` runs, which would either trigger the
incompatible-provider check or cause autocommit failures.

`MongoServiceRegistryProducer` is a new interface in the integration test
source set that test classes implement instead of `ServiceRegistryProducer`
directly. Its default `produceServiceRegistry` implementation:

1. Reapplies `@ServiceRegistry` annotation settings (which would otherwise be
   lost when bypassing `ServiceRegistryProducerImpl`)
2. Removes `CONNECTION_PROVIDER` from the builder so `contribute()` sees null
   and auto-configures cleanly
3. Resets `CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT` to false

All integration test classes — both top-level and `@Nested` — implement the
interface. `JakartaPersistenceBootstrappingIntegrationTests` (the one `@Jpa`
test) required a different approach since `@Jpa` goes through
`EntityManagerFactoryExtension`, not `ServiceRegistryExtension`. That test
was deleted: before this commit it verified that explicit
`hibernate.connection.provider_class` configuration via `@Jpa.integrationSettings`
produced a working session factory, which was meaningful when explicit
configuration was required. Now that the connection provider is auto-configured,
the test exercised no JPA-specific behavior and its dialect came from
`hibernate.properties` (a Hibernate mechanism, not a JPA one).

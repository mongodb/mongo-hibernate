# HIBERNATE-171: Auto-infer dialect from MongoDB JDBC URL

## Goal

Users should be able to configure the MongoDB extension with only the JDBC URL:

```properties
jakarta.persistence.jdbc.url=mongodb://localhost/mydb
```

Both `hibernate.dialect` and `hibernate.connection.provider_class` are inferred automatically.

## Context

HIBERNATE-124 already auto-configures `MongoConnectionProvider` when
`hibernate.dialect=MongoDB` is set. HIBERNATE-171 is the remaining step:
auto-set the dialect from the URL so it also does not need to be set.

## Production Change

In `ServiceContributor.contribute()` in `StandardServiceRegistryScopedState`,
before the existing `if (isMongoDialect(...))` block:

```java
var url = settings.get(JAKARTA_JDBC_URL);
if (settings.get(AvailableSettings.DIALECT) == null
        && url instanceof String urlString
        && (urlString.startsWith("mongodb://") || urlString.startsWith("mongodb+srv://"))) {
    serviceRegistryBuilder.applySetting(AvailableSettings.DIALECT, MONGO_DIALECT_SHORT_NAME);
}
```

`JAKARTA_JDBC_URL` is the only URL setting checked, consistent with
`MongoConfigurationBuilder` which also reads only this setting.

Existing behavior is preserved for all other cases:
- Explicit dialect set → inference skipped (dialect already present)
- Non-MongoDB URL or no URL → inference skipped, existing behavior unchanged
- Non-MongoDB dialect alongside MongoDB URL → silent, no error from our code
  (same as before: `checkMongoDialectIsPluggedIn` only fires if
  `StandardServiceRegistryScopedState` is actually requested)

## Test Property Changes

Remove `hibernate.dialect=MongoDB` from:
- `src/test/resources/hibernate.properties`
- `src/integrationTest/resources/hibernate.properties`

All existing integration tests implicitly exercise the `mongodb://` inference
path, providing good coverage.

## New Unit Tests

In `StandardServiceRegistryScopedStateTests`:

**Tests that call `ServiceContributor.contribute()` directly** (without `build()`)
to avoid triggering `createMongoConfiguration` or DNS resolution for `mongodb+srv://`:

1. `mongodb://` URL, no dialect → dialect inferred as `"MongoDB"`
2. `mongodb+srv://` URL, no dialect → dialect inferred as `"MongoDB"`
3. `mongodb://` URL, explicit dialect already set → dialect not overridden

Pattern:
```java
var builder = new StandardServiceRegistryBuilder()
    .clearSettings()
    .applySetting(JAKARTA_JDBC_URL, "mongodb://host/db");
new StandardServiceRegistryScopedState.ServiceContributor().contribute(builder);
assertThat(builder.getSettings().get(AvailableSettings.DIALECT))
    .isEqualTo(MONGO_DIALECT_SHORT_NAME);
```

**Test that calls `build()`** to confirm non-MongoDB dialect + MongoDB URL
produces no error from our code (inference is skipped when dialect is explicit):

```java
assertThatCode(() -> new StandardServiceRegistryBuilder()
        .clearSettings()
        .applySetting(DIALECT, "org.hibernate.dialect.H2Dialect")
        .applySetting(JAKARTA_JDBC_URL, "mongodb://host/db")
        .build()
        .close())
    .doesNotThrowAnyException();
```

## Out of Scope

- `hibernate.connection.url` detection (not read by `MongoConfigurationBuilder`)
- `mongodb+srv://` integration test (synchronous DNS lookup in `ConnectionString`)
- Warning or error when non-MongoDB dialect is set alongside MongoDB URL

# HIBERNATE-183: Required null-semantics configuration property

## Context

MongoDB's null-comparison behavior varies by expression and pipeline stage, and does not
match SQL's three-valued logic. Rather than pick a default now, this ticket adds a
required configuration property that forces every user to declare their expectation.
Only one value is supported today (`MQL`); a future release may add `SQL` semantics and
possibly change the default, but users who declared `MQL` today keep getting the actual
MQL behavior, not a silently-changed default.

The value is intentionally named `MQL`, not `binary` or `twoValued`: it does not assert a
coherent two-valued semantics exists. It documents that null semantics are "whatever MQL's
translation happens to do," which is not part of the contract and may change release to
release.

## Property

- **Key:** `com.mongodb.hibernate.semantics.nulls` (string literal, see "Naming" below)
- **Value:** `String`, only `"MQL"` is accepted (exact, case-sensitive match)
- **Default:** none — the property is required
- **Storage:** not threaded into `MongoConfiguration` or anywhere else. This ticket is a
  pure bootstrap-time gate; nothing consumes the value yet. A future ticket that adds
  `SQL` semantics will introduce the plumbing to read and act on it.

### Naming

`semantics` is a deliberate namespace, not a flattened `nullSemantics` key, anticipating a
family of MQL-vs-SQL behavioral divergences that may eventually get their own properties,
e.g.:

- `semantics.arrayEquality` — MongoDB fields can hold arrays; comparing a field to a
  scalar matches if *any* array element matches, which SQL has no analog for
- `semantics.typeComparison` — cross-BSON-type comparisons follow BSON's type-ordering
  rules, not SQL's coercion/error rules
- `semantics.nullOrdering` — where nulls/missing fields land in `$sort` vs SQL's
  `NULLS FIRST/LAST`
- `semantics.collation` — string comparison case-sensitivity/locale, since MQL has an
  explicit `collation` option SQL dialects handle differently
- `semantics.aggregateNulls` — whether aggregates skip nulls/missing fields the way SQL
  aggregates do
- `semantics.joinMissingMatch` — `LEFT JOIN` unmatched-is-null vs `$lookup`
  unmatched-is-empty-array semantics

None of these are being added now (YAGNI) — this is just the reasoning for keeping
`semantics` as a namespace prefix.

The key uses plural `nulls`, not singular `null`: technically `null` would be legal here
(it's a string literal property key, not a Java identifier, so the language-keyword
restriction doesn't apply), but plural sidesteps the awkwardness of a bare `null` segment
and reads naturally as a noun ("null semantics"), consistent with the other candidate
names above.

No public constant is introduced for the key or value, following the existing precedent of
`MongoConstants.MONGO_CONFIGURATION_CONTRIBUTOR_KEY`: it stays an internal string literal,
documented via javadoc and exception text, not a symbol users import.

## Validation

Add a new private static method to `StandardServiceRegistryScopedState.ServiceContributor`
(`/Users/jeff/git/m/mongo-hibernate/src/main/java/com/mongodb/hibernate/internal/service/StandardServiceRegistryScopedState.java`),
sibling to the existing `forbidTemporalConfiguration`:

```java
private static void checkNullSemantics(Map<String, Object> configurationValues) {
    var value = configurationValues.get("com.mongodb.hibernate.semantics.nulls");
    if (value == null) {
        throw new HibernateException(
                "Configuration property [com.mongodb.hibernate.semantics.nulls] is required");
    }
    if (!"MQL".equals(value)) {
        throw new HibernateException(format(
                "Configuration property [com.mongodb.hibernate.semantics.nulls] with value [%s] must be [MQL]",
                value));
    }
}
```

Called from `createMongoConfiguration(...)` alongside `forbidTemporalConfiguration(...)`,
so it runs unconditionally whenever the Mongo-specific service is actually initiated —
i.e., only for genuine Mongo bootstraps. It does not run for non-Mongo dialect bootstraps
(e.g. `NativeBootstrappingTests`), because `StandardServiceRegistryScopedState` is never
requested in that path; `checkMongoDialectIsPluggedIn` would reject before reaching here
if a Mongo bootstrap were misconfigured, but that's a separate, already-existing check.

Unlike `JAKARTA_JDBC_URL`, this check is **not** waived when a `MongoConfigurationContributor`
is present — there is no builder-method equivalent for this property, so a contributor
can't supply it.

## Documentation

Add a row to the "Supported configuration properties" table in
`MongoConfigurator`'s class javadoc
(`/Users/jeff/git/m/mongo-hibernate/src/main/java/com/mongodb/hibernate/cfg/MongoConfigurator.java`):

| Method | Has default | Property name | Supported value types | Value |
|---|---|---|---|---|
| — | ✗ | `com.mongodb.hibernate.semantics.nulls` | `String` | Must be `"MQL"` (the only supported value). Declares that null-comparison semantics are whatever MQL's translation produces, which is not part of the contract and may change release to release. A future release may add `"SQL"` semantics. |

## Tests

All in
`/Users/jeff/git/m/mongo-hibernate/src/test/java/com/mongodb/hibernate/internal/service/StandardServiceRegistryScopedStateTests.java`:

| Test | Covers |
|---|---|
| `testNullSemanticsRequired` (new) | Property absent → `HibernateException` with the "is required" message |
| `testNullSemanticsUnsupportedValue` (new) | Property present but not `"MQL"` → `HibernateException` with the "must be [MQL]" message, including the actual value |
| `testNullSemanticsMql` (new) | Property present and `"MQL"` → no exception |
| `contributorFromConfigurationValuesIsInvoked` (existing) | Update: this test explicitly clears settings and applies only `JAKARTA_JDBC_URL`/`DIALECT`, so it needs an explicit `.applySetting("com.mongodb.hibernate.semantics.nulls", "MQL")` added or it will start failing |

Service initiation is lazy: `StandardServiceRegistryBuilder#build()` alone does not run
`initiateService()`. Each new test must call
`registry.requireService(StandardServiceRegistryScopedState.class)` after building (inside
a try-with-resources on the registry), matching the pattern already used by
`testMongoDialectNotPluggedIn` and `contributorFromConfigurationValuesIsInvoked`.

## Blast radius: existing bootstrap sites that must add the property

Because the property is required, every existing place that bootstraps a real Mongo
Hibernate registry needs `com.mongodb.hibernate.semantics.nulls=MQL` added, or it will
start failing:

1. `src/test/resources/hibernate.properties` — add the property (covers unit tests that
   rely on classpath defaults, including `differentStandardServiceRegistriesHaveDifferentStates`
   and all of `MongoConfigurationContributorTests`)
2. `src/integrationTest/resources/hibernate.properties` — add the property
3. `mongodb-hibernate-spring-boot-autoconfigure/src/integrationTest/resources/application.properties`
   — add `spring.jpa.properties.com.mongodb.hibernate.semantics.nulls=MQL`
4. `example-module/src/main/java/com/mongodb/hibernate/example/AppWithMongoConfiguratorContributorAddedDirectly.java`
   — add `.applySetting("com.mongodb.hibernate.semantics.nulls", "MQL")`
5. `example-module/src/main/java/com/mongodb/hibernate/example/AppWithMongoConfiguratorContributorAddedViaServiceContributor.java`
   — same
6. `example-module/src/smokeTest/java/com/mongodb/hibernate/example/test/MongoAdditionalMappingContributorTests.java`
   — same, defensively (uses `buildMetadata`, which may or may not trigger service
   initiation; add it regardless since it's cheap insurance)
7. `README.md` Spring Boot config snippet (currently `spring.jpa.database-platform` /
   `spring.mongodb.uri`) — add
   `spring.jpa.properties.com.mongodb.hibernate.semantics.nulls=MQL` plus a one-sentence
   explanation

### Explicitly NOT changed (verified the check never fires there)

- `mongodb-hibernate-spring-boot-autoconfigure/src/integrationTest/resources/application-h2.properties`
  — non-Mongo dialect
- `src/test/java/com/mongodb/hibernate/boot/NativeBootstrappingTests.java` — non-Mongo
  dialect, `clearSettings()` + H2
- `src/integrationTest/java/com/mongodb/hibernate/TestServiceContributor.java` — doesn't
  set Hibernate settings, only registers services
- Spring Boot starter's `MongoHibernateAutoConfiguration` — deliberately does **not**
  auto-default this property (decided explicitly): the whole point is forcing every user,
  including Spring Boot users, to consciously declare null semantics before an alternative
  exists. Auto-defaulting would silently exempt Spring Boot users from that decision.

## Out of scope

- Actually consuming the value anywhere (translator, dialect). This ticket only gates
  bootstrap.
- Adding `SQL` as a second supported value, or any of the other `semantics.*` properties
  brainstormed under "Naming" above.

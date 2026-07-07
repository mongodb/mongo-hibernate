# HIBERNATE-73: Forbid unsupported BSON / UUID domain types at boot

Jira: https://jira.mongodb.org/browse/HIBERNATE-73

## Problem

Many BSON-related Java types are `Serializable`. When an entity maps a field of such a type
and we provide no explicit Hibernate type for it, Hibernate falls back to Java binary
serialization: it stores the value via `PreparedStatement.setBytes` as a BSON binary
(subtype 0) holding the serialized object graph. The value "works" (round-trips) today, but
the on-disk representation is wrong and non-interoperable. Introducing proper support for
these types after the product is public would therefore be a **breaking change**.

Confirmed empirically:

- `java.util.UUID` field → persisted as 16 raw bytes with binary **subtype 0** (a standard
  UUID should be subtype 4). Looks like it "works", silently wrong.
- `org.bson.types.BSONTimestamp` field → persisted as Java-serialized bytes
  (`AC ED 00 05 …`), subtype 0.
- `org.bson.BsonDocument` field → persisted as Java-serialized bytes, subtype 0.
- `org.bson.Document` (the most common driver type) → same `Serializable` silent-serialization
  path.

The goal of this ticket is to make these fail **at `SessionFactory` build time** with a
clear, per-attribute message, before the product is public, so that adding real support later
is a feature addition rather than a breaking change.

## Key discovery driving the design

`org.bson.Document`, `org.bson.BsonDocument` (and subclasses `RawBsonDocument`,
`BsonDocumentWrapper`), all `org.bson.types.*` value types, and `java.util.UUID` are
`Serializable`; Hibernate resolves them (via the `Serializable` fallback, or a built-in
mapping for `UUID`) and would silently persist them.

By contrast, the non-`Serializable` BSON types — `org.bson.BinaryVector` (and its subtypes),
`org.bson.BsonValue` and all its non-`Document` subtypes (`BsonInt32`, `BsonString`,
`BsonArray`, …), and `org.bson.json.JsonObject` — Hibernate **cannot resolve at all**, so a
field of such a type already **fails at boot** today with
`org.hibernate.type.descriptor.java.spi.JdbcTypeRecommendationException: Could not determine
recommended JdbcType`. These are not a silent-corruption risk; they merely produce a less
friendly error message.

Therefore the silent-corruption / breaking-change danger is **exactly** the `Serializable`
set, and that set is what this ticket forbids explicitly.

## Mechanism

The codebase already has the right hook: `MongoAdditionalMappingContributor`
(`internal/boot/`). At boot it walks every entity's persistent attributes — recursing into
embeddable components and into plural (collection/array) element types — and throws a clear
`FeatureNotSupportedException` for unsupported types via an `UNSUPPORTED_TYPES` set and a
`forbidTemporalTypes(...)` check. It already forbids the JDBC/temporal types
(`java.util.Date`, `Calendar`, `LocalDateTime`, etc.) this way, with messages like:

> `RootClass(...Item): the persistent attribute [date] has type [java.util.Date] that is not
> supported`

Implementation:

- Add the forbidden `Serializable` classes (below) to the `UNSUPPORTED_TYPES` set.
- Rename `forbidTemporalTypes` to `forbidUnsupportedTypes` (it is no longer temporal-specific);
  its logic and the two message templates (singular / plural element) are unchanged and reused.
- No changes to the traversal — the existing recursion already covers direct fields,
  embeddable fields, and collection/array element types (so `List<UUID>`, `UUID[]`, and
  embeddable fields of these types are all caught).

Because this check reads the *resolved* attribute type, it fires precisely for the
`Serializable` (resolvable) set; the non-`Serializable` types fail earlier in Hibernate's own
resolution and never reach it (see Scope → out of scope).

The forbidding is by **domain type** (`type.getReturnedClass()`), matching the existing
temporal forbidding exactly. It is therefore **absolute**: an `AttributeConverter` does **not**
provide an escape hatch (a converted attribute still resolves with the domain type as its
returned class, so the check still fires). This was confirmed empirically — `@Convert(UUID →
String)` still fails at boot once `UUID` is in `UNSUPPORTED_TYPES`. This is intentional and
consistent with how temporal types already behave; there is no per-attribute opt-out. Users who
need one of these types must wait for its native-support ticket. (Note: standard Hibernate has
no such forbidding and would silently binary-serialize these types — e.g. to `bytea` on
Postgres — which is precisely the behavior this extension rejects.)

### Message

Reuse the existing contributor message format (no `TODO-HIBERNATE-NNN` reference): this ticket
*implements* the ban, and no per-type support ticket is filed yet, so a `TODO-` tag would be
stale. When a support ticket is later filed for a specific type, the message can be revisited.

## Scope

### Forbidden (added to `UNSUPPORTED_TYPES`)

All are `Serializable` and would otherwise be silently persisted:

- `org.bson.types.*` value types **except `ObjectId`** (which is natively supported):
  `BSONTimestamp`, `Binary`, `Code`, `CodeWithScope`, `CodeWScope`, `MinKey`, `MaxKey`,
  `Symbol`, `Decimal128`. `Decimal128` is forbidden only as a *field type*; the supported
  decimal path is `BigDecimal` (stored as BSON `Decimal128`), which is unaffected.
- `org.bson.Document`
- `org.bson.BsonDocument`, `org.bson.RawBsonDocument`, `org.bson.BsonDocumentWrapper`
  (each listed explicitly — the check matches by exact class).
- `java.util.UUID`

### Out of scope

1. **Non-`Serializable` BSON types** — `org.bson.BinaryVector` (+ `Float32BinaryVector`,
   `Int8BinaryVector`, `PackedBitBinaryVector`), `org.bson.BsonValue` and its non-`Document`
   subtypes, and `org.bson.json.JsonObject`. These already fail at boot via Hibernate's own
   `JdbcTypeRecommendationException` (non-silent). We intentionally do **not** add extra
   machinery just to re-message them. A test documents that they fail at boot.
2. **`org.bson.types.BasicBSONList`** — a `List` (collection type), not a scalar value type a
   user would map as a field, so it is deliberately left out of `UNSUPPORTED_TYPES`.
3. **Read-side binary-subtype guard** — rejecting reads of BSON binary subtypes we don't
   support (e.g. 3/4 for UUID, 9 for vectors) in `ValueConversions` / `MongoResultSet`. A
   runtime read-path concern sharing no code with boot-time mapping forbidding; its own
   ticket/PR. Not filed yet.
4. **Real support** for `UUID`, raw `Decimal128` fields, `Binary`, `BinaryVector` (vector
   search), etc. — each a possible future feature, its own ticket. Not filed yet.

None of these types are "permanently" unsupported; they are simply not supported yet.

## Risks / verification

- **`@Struct` / embeddables use `BsonDocument` at the JDBC boundary** (`MongoStructJdbcType`,
  `ValueConversions` passthrough). That is not a mapped *domain* property type — an embeddable
  property resolves to a `ComponentType` (handled by the contributor's component branch,
  recursing into the embeddable's real fields), never to a `BsonDocument`-typed attribute. So
  adding `BsonDocument` to `UNSUPPORTED_TYPES` is expected not to affect `@Struct`. **Verify**
  by running the existing embeddable/struct integration tests for regressions.
- Run the full existing suite for regressions (nothing currently maps a forbidden type).

## Tests

The only genuinely reusable test infrastructure is `assertNotSupported` (currently a static in
`type/temporal/CalendarIntegrationTests`). The generic `ItemWith*<T>` base classes in
`type/temporal/UnsupportedItems` are **not** reusable — Hibernate 7 cannot resolve the
type-token pattern, so each type already has its own fully-concrete copy and the generics are
referenced nowhere. So: extract only `assertNotSupported` to a neutral shared location under
`type/`; the temporal test files stay in `type/temporal/` and import it from there. New per-type
entity classes are written concretely (as temporal does), not via generics.

New test class (e.g. `type/UnsupportedTypesIntegrationTests`) following the temporal pattern —
a plain JUnit class (no `@SessionFactory` / `@DomainModel` / `MongoServiceRegistryProducer`; the
shared `assertNotSupported` builds metadata directly and asserts the exception, so no running
MongoDB is needed):

- Negative tests asserting that building the metadata for an entity with a **basic attribute**
  of each forbidden `Serializable` type throws `FeatureNotSupportedException` with the expected
  "has type [...] that is not supported" message (via the shared `assertNotSupported`), using a
  concrete per-type entity class. Cover:
  - `org.bson.types`: `BSONTimestamp`, `Binary`, `Code`, `CodeWithScope`, `CodeWScope`,
    `MinKey`, `MaxKey`, `Symbol`, `Decimal128`.
  - `org.bson.Document`, `org.bson.BsonDocument`, `org.bson.RawBsonDocument`,
    `org.bson.BsonDocumentWrapper`.
  - `java.util.UUID`.
- For **one representative type** (`UUID`), also assert failure in each attribute *shape*, to
  confirm the contributor's non-basic branches fire for a new type: as an **`@Id`** (a common
  JPA primary-key pattern this now rejects), as a **collection element**, as a **flattened
  embeddable field**, and as a **`@Struct` aggregate embeddable field**. The full temporal
  matrix already exercises these branches exhaustively; this is a representative spot-check, not
  a re-run of the whole matrix.
- One test asserting a representative non-`Serializable` type (`org.bson.BsonInt32`) also fails
  at boot — asserting a throwable whose message contains the type name (Hibernate's own
  `JdbcTypeRecommendationException`), not `FeatureNotSupportedException`.
- One negative test asserting that a forbidden type **with an `AttributeConverter`** (a `UUID`
  field annotated with `@Convert` to `String`) **still** fails at boot with
  `FeatureNotSupportedException` — locking in the "no escape hatch" behavior.
- Existing supported types (`ObjectId`, `BigDecimal`, `byte[]`, `Instant`, primitives,
  `String`) must still build and round-trip (existing tests cover this; ensure they pass).

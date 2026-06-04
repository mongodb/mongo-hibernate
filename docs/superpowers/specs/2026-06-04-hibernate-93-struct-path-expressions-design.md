# HIBERNATE-93: Support path expressions for @Struct aggregate embeddables

**Date:** 2026-06-04  
**Jira:** https://jira.mongodb.org/browse/HIBERNATE-93  
**Status:** Design approved  

---

## Problem

HQL path expressions that traverse into a `@Struct`-annotated `@Embeddable` field (e.g. `s.nested.a`) produce broken
MQL. The `$match`, `$sort`, and `$set` stages receive a TODO debug string as the field key instead of the correct
MongoDB dot-notation path (`nested.a`). The JDBC layer detects this string via `MongoAggregateSupport.checkSupported()`
and throws `FeatureNotSupportedException`.

**Affected operations:**
- `FROM ItemWithNestedValue WHERE nested.a = 0`
- `FROM ItemWithNestedValue ORDER BY nested.a`
- `SELECT nested.a FROM ItemWithNestedValue`
- `UPDATE ItemWithNestedValue SET nested.a = 0`

---

## Root Cause

`MongoAggregateSupport` overrides two `AggregateSupport` methods that Hibernate calls during bootstrapping
(`AggregateComponentSecondPass`) to set up column mappings for aggregate (struct) types:

- `aggregateComponentAssignmentExpression` — the returned string becomes `Column.assignmentExpression`, which feeds into
  `ColumnReference.getColumnExpression()`. The MQL translator uses this as the field key in `$match`, `$sort`, and
  `$set`.
- `aggregateComponentCustomReadExpression` — the returned string becomes `Column.customReadExpression` /
  `ColumnReference.readExpression`. Used for scalar projections.

Both currently return a debug string (`TODO-HIBERNATE-93 ...`) instead of the correct dot-notation path.

---

## MongoDB Mechanism

MongoDB uses dot notation natively for nested document field access.

**$match (WHERE):**
```json
{"$match": {"nested.a": {"$eq": 0}}}
```

**$sort (ORDER BY):**
```json
{"$sort": {"nested.a": 1}}
```

**$set (UPDATE):**
```json
{"$set": {"nested.a": 0}}
```

**$project (scalar SELECT) — requires `#` alias:**

`MongoResultSet` reads result values by calling `BsonDocument.get(fieldName)` — a literal key lookup, not a path
traversal. Projecting `{"nested.a": true}` causes MongoDB to return `{"nested": {"a": 2}}`, and `get("nested.a")`
returns null.

The fix (same pattern as HIBERNATE-145 join projections) is to use `#` as the path separator in the projection key and a
`$field.path` expression as the value:

```json
{"$project": {"nested#a": "$nested.a"}}
```

MongoDB returns `{"nested#a": 2}` and `get("nested#a")` returns the integer directly.
`AstProjectStageFieldPathSpecification` (introduced in HIBERNATE-145) renders this form.

Nested structs compose correctly: `outer.inner.field` in `$match`/`$sort`/`$set`, and `outer#inner#field` as the
`$project` key with `"$outer.inner.field"` as the value.

---

## Implementation

This work depends on `AstProjectStageFieldPathSpecification` and the `MongoStatement.isExcludeProjectSpecification` update
(which allows `$`-prefixed string values in `$project`), both introduced by HIBERNATE-145 and already present on `main`.

### 1. `MongoStructJdbcType.java`

Add `public static final int HIBERNATE_SQL_TYPE = SqlTypes.STRUCT` to match the existing
`MongoArrayJdbcType.HIBERNATE_SQL_TYPE` pattern. The type-code check in `MongoAggregateSupport` (next section) then reads
consistently:

```java
aggregateColumnTypeCode == MongoStructJdbcType.HIBERNATE_SQL_TYPE
    || aggregateColumnTypeCode == MongoArrayJdbcType.HIBERNATE_SQL_TYPE
```

### 2. `MongoAggregateSupport.java`

Replace both old-style method overrides (which take `AggregateColumn, Column`) with the Hibernate 7 signatures (which
take `int aggregateColumnTypeCode`). The type-code guard is unchanged — same constants, now passed directly as `int`,
checked against the `HIBERNATE_SQL_TYPE` constants from section 1.

**Verified empirically** (instrumented the methods, ran `StructAggregateEmbeddableIntegrationTests` bootstrap): with both
the old and new signatures overridden, Hibernate 7.4 calls only the **old** (`AggregateColumn`) signatures. But when only
the **new** signatures are overridden, the superclass's old-signature default delegates to them, so the new overrides
fire (116 assignment + 112 read calls observed) and our logic still runs. Switching to the new signatures is therefore
safe. The type codes passed are `2002` for `STRUCT` and `3016` for `STRUCT_ARRAY`, matching `SqlTypes.STRUCT` /
`SqlTypes.STRUCT_ARRAY`.

Both new-signature methods produce the same value — the parent path plus `.` plus the column — so they delegate to a
shared `aggregateComponentExpression` helper, with the type guard factored into `isStructOrArrayType`:
```java
@Override
public String aggregateComponentAssignmentExpression(
        String aggregateParentAssignmentExpression,
        String columnExpression,
        int aggregateColumnTypeCode,
        Column column) {
    return aggregateComponentExpression(
            aggregateParentAssignmentExpression, columnExpression, aggregateColumnTypeCode);
}

@Override
public String aggregateComponentCustomReadExpression(
        String template,
        String placeholder,
        String aggregateParentReadExpression,
        String columnExpression,
        int aggregateColumnTypeCode,
        SqlTypedMapping column,
        TypeConfiguration typeConfiguration) {
    return aggregateComponentExpression(aggregateParentReadExpression, columnExpression, aggregateColumnTypeCode);
}

private static String aggregateComponentExpression(
        String aggregateParentExpression, String columnExpression, int aggregateColumnTypeCode) {
    if (isStructOrArrayType(aggregateColumnTypeCode)) {
        return aggregateParentExpression + "." + columnExpression;
    }
    throw new FeatureNotSupportedException(
            format("The SQL type code [%d] is not supported", aggregateColumnTypeCode));
}

private static boolean isStructOrArrayType(int aggregateColumnTypeCode) {
    return aggregateColumnTypeCode == MongoStructJdbcType.HIBERNATE_SQL_TYPE
            || aggregateColumnTypeCode == MongoArrayJdbcType.HIBERNATE_SQL_TYPE;
}
```

`isStructOrArrayType` is also used by `requiresAggregateCustomWriteExpressionRenderer`, replacing its prior
`MongoStructJdbcType.JDBC_TYPE.getVendorTypeNumber()` check — `SqlTypes.STRUCT == java.sql.Types.STRUCT`, so the guard is
equivalent and now uniform across all three methods.

The value returned for `aggregateComponentCustomReadExpression` is not actually used by the MQL translator. Investigation
showed that it feeds `ColumnReference.readExpression`, which `visitColumnReference` ignores — the MQL translator only
reads `getColumnExpression()`, which comes from `aggregateComponentAssignmentExpression`. The read expression is only
consumed by `AbstractSqlAstTranslator.appendReadExpression`, a SQL rendering path MongoDB never takes, and by
`visitNestedColumnReference` which throws `FeatureNotSupportedException` in our translator.

`aggregateComponentCustomReadExpression` must still return a value (throwing breaks bootstrap), which is why it shares
the helper rather than being elided. That value need not be a clean dot path. The instrumented run confirmed how `{@}`
(`Template.TEMPLATE`) flows:

- **Scalar leaf assignment expressions — the keys `$match`/`$sort`/`$set` actually use — come out clean.** The methods
  are invoked once per nesting level; Hibernate strips a leading `{@}.` from the parent before processing each level's
  sub-columns. Observed: `parent=[nested] column=[a]` → `nested.a`; `parent=[nested2.nested] column=[bigDecimal]` →
  `nested2.nested.bigDecimal`. No scalar leaf key contains `{@}`.
- **`{@}` survives only in places the translator never uses as a query key:** the intermediate template built for a
  nested *struct column* (observed `parent=[{@}.nested2] column=[nested]` → `{@}.nested2.nested`, which Hibernate strips
  back to `nested2.nested` before the next level), and the read expression — notably the bare-`{@}` array read seed,
  where `{@}` has no trailing dot and is not stripped. The read expression is never consumed by the MQL translator (see
  above), so its `{@}` is harmless.

**Remove entirely:**
- `UNSUPPORTED_MESSAGE_PREFIX` constant
- `checkSupported(String mql)` static method
- the now-unused `AggregateColumn` import (the new signatures take `int aggregateColumnTypeCode` instead)

### 3. `MongoStatement.java` and `MongoPreparedStatement.java`

Remove all calls to `MongoAggregateSupport.checkSupported(mql)` (four call sites total): `MongoPreparedStatement`
constructor (one), and `MongoStatement.executeQuery`, `executeUpdate`, and `execute` (three).

### 4. `AbstractMqlTranslator.java` — `$project` stage

When building `$project` specifications, struct path expressions (dotted field paths *not* produced by a join) must also
use `AstProjectStageFieldPathSpecification` instead of `AstProjectStageIncludeSpecification`. This is an **additional**
branch — the existing join-projection branch must be preserved exactly, because join field paths are also dotted but
require different key handling (the leading `#` join-alias prefix must be stripped by `joinFieldProjectionKey`, which a
plain `.`→`#` replace does not do).

The current code is:

```java
var field = acceptAndYield(columnReference, FIELD_PATH);
AstProjectStageSpecification spec = field.startsWith(JOIN_ALIAS_PREFIX)
        ? new AstProjectStageFieldPathSpecification(joinFieldProjectionKey(field), field)
        : new AstProjectStageIncludeSpecification(field);
projectStageSpecifications.add(spec);
```

Add a struct-path branch that fires only for non-join dotted paths, leaving the join branch untouched, with a
`nestFieldProjectionKey` helper paralleling `joinFieldProjectionKey`:

```java
var field = acceptAndYield(columnReference, FIELD_PATH);
AstProjectStageSpecification spec;
if (field.startsWith(JOIN_ALIAS_PREFIX)) {
    spec = new AstProjectStageFieldPathSpecification(joinFieldProjectionKey(field), field);
} else if (field.contains(".")) {
    spec = new AstProjectStageFieldPathSpecification(nestFieldProjectionKey(field), field);
} else {
    spec = new AstProjectStageIncludeSpecification(field);
}
projectStageSpecifications.add(spec);
```

```java
private static String nestFieldProjectionKey(String field) {
    return field.replace('.', '#');
}
```

`AstProjectStageFieldPathSpecification(key, path)` renders `{key: "$path"}`. For struct paths, `nestFieldProjectionKey`
converts `nested.a` → `nested#a` and `outer.inner.a` → `outer#inner#a`. Join paths (`#qualifier.field`) keep going
through `joinFieldProjectionKey`, which strips the leading `#` first (`#o1_0.total` → `o1_0#total`); applying
`nestFieldProjectionKey` to them directly would wrongly yield `#o1_0#total` and break HIBERNATE-145 join-fetch
projections.

---

## Tests

### Entities

**Single-level** (already exists in `SimpleSelectQueryIntegrationTests.Unsupported` and
`UpdatingIntegrationTests.Unsupported`):

```java
// StructAggregateEmbeddableIntegrationTests.Single — @Struct with Integer a

@Entity(name = "ItemWithNestedValue")
@Table(name = COLLECTION_NAME)
static class ItemWithNestedValue {
    @Id int id;
    StructAggregateEmbeddableIntegrationTests.Single nested;
}
```

**Two-level** (new, defined inline in `SimpleSelectQueryIntegrationTests`):

```java
@Embeddable
@Struct(name = "OuterStruct")
static class OuterStruct {
    InnerStruct inner;
}

@Embeddable
@Struct(name = "InnerStruct")
static class InnerStruct {
    int a;
}

@Entity(name = "ItemWithDeeplyNestedValue")
@Table(name = COLLECTION_NAME)
static class ItemWithDeeplyNestedValue {
    @Id int id;
    OuterStruct outer;
}
```

Both entities share `COLLECTION_NAME`. The new entity class and its structs are added to the outer-class `@DomainModel`.

### Changes to `SimpleSelectQueryIntegrationTests`

`ItemWithNestedValue` is already in the outer-class `@DomainModel`. Add `ItemWithDeeplyNestedValue`, `OuterStruct`, and
`InnerStruct`.

**Convert from negative to positive** (two existing `Unsupported` tests):

| Old test (Unsupported) | HQL | Key MQL assertion |
|------------------------|-----|-------------------|
| `testStructAggregateEmbeddablePathExpressionSelection` | `select nested.a from ItemWithNestedValue` | `$project: {"nested#a": "$nested.a"}` |
| `testStructAggregateEmbeddablePathExpressionComparison` | `from ItemWithNestedValue where nested.a = 0` | `$match: {"nested.a": {$eq: 0}}` |

**Add new positive tests**:

| Test | HQL | Key MQL assertion |
|------|-----|-------------------|
| `testStructAggregateEmbeddablePathExpressionSelectAndFilter` | `select nested.a from ItemWithNestedValue where nested.a = 2` | `$match: {"nested.a": ...}`, `$project: {"nested#a": "$nested.a"}` |
| `testStructAggregateEmbeddableTwoLevelPathExpression` | `from ItemWithDeeplyNestedValue where outer.inner.a = 1` | `$match: {"outer.inner.a": {$eq: 1}}` |
| `testStructAggregateEmbeddableTwoLevelPathExpressionSelection` | `select outer.inner.a from ItemWithDeeplyNestedValue` | `$project: {"outer#inner#a": "$outer.inner.a"}` |
| `testStructAggregateEmbeddableMultiFieldProjection` | `select pair.a, pair.b from ItemWithPair` | `$project: {"pair#a": "$pair.a", "pair#b": "$pair.b"}` (multiple keys) |

The multi-field cases use a new two-field struct `Pair {int a; int b;}` / entity `ItemWithPair`.

**`STRUCT_ARRAY` branch coverage**

The two fixed methods guard on `MongoArrayJdbcType.HIBERNATE_SQL_TYPE` (`STRUCT_ARRAY`) as well as
`MongoStructJdbcType.HIBERNATE_SQL_TYPE` (`STRUCT`). The `STRUCT_ARRAY` branch is reached at **bootstrap time**
(`AggregateComponentSecondPass` sets up column read/assignment expressions for a `@Struct` array's component columns) —
not at query time, so it must be covered with the Phase 3 coverage markers rather than via a query.

The instrumented run **confirmed the `STRUCT_ARRAY` branch is live, not dead code**: type code `3016`
(`SqlTypes.STRUCT_ARRAY`) is passed at bootstrap when a `@Struct` array field is present (e.g.
`StructAggregateEmbeddableIntegrationTests.ItemWithNestedValueHavingArraysAndCollections` has
`Single[] structAggregateEmbeddables`). One caveat for the Phase 3 markers: they only exercise `STRUCT_ARRAY` when the
**running test's `@DomainModel` actually contains a `@Struct` array field**. The HIBERNATE-93 test classes
(`SimpleSelectQueryIntegrationTests`, `UpdatingIntegrationTests`) do **not** today — run the markers against
`StructAggregateEmbeddableIntegrationTests`, or add a struct-array entity to a HIBERNATE-93 test class.

There is intentionally **no query-based test** for element-wise access into a struct array, because no such HQL path is
reachable today:

- An implicit dotted path (`from CartLike where lineItems.sku = :v`) is **not legal HQL** — `lineItems` is a plural
  (array) attribute, and JPQL forbids implicitly dereferencing a collection-valued path. Hibernate raises a
  `SemanticException` during SQM build, before our translator runs.
- The explicit-join form (`from CartLike c join c.lineItems li where li.sku = :v`) is legal HQL but throws
  `FeatureNotSupportedException` `TODO-HIBERNATE-111` (no `$unwind`/unnest set-returning function for struct arrays). See
  the existing probe in `JoinSelectQueryIntegrationTests`. HIBERNATE-111 is in code review and will unblock this form on
  a forthcoming branch; element-wise struct-array querying is tracked there, out of scope for HIBERNATE-93.

### Changes to `SortingSelectQueryIntegrationTests`

**Convert from negative to positive** (one existing `Unsupported` test):

| Old test (Unsupported) | HQL | Key MQL assertion |
|------------------------|-----|-------------------|
| `testStructAggregateEmbeddablePathExpressionSorting` | `from ItemWithNestedValue order by nested.a` | `$sort: {"nested.a": 1}` |

### Changes to `NativeQueryIntegrationTests`

Removing `UNSUPPORTED_MESSAGE_PREFIX` changes the failure surfaced by the existing negative test
`testEntityWithAggregateEmbeddableValue` (loading a `@Struct`-embeddable entity via a native query). The case stays
unsupported — it is the upstream bug
[HHH-19866](https://hibernate.atlassian.net/browse/HHH-19866) (native entity queries mishandle
`preferSelectAggregateMapping == true`, requesting leaf columns like `nested.a` that aren't projected). The dialect no
longer emits the TODO string, so `MongoResultSet.findColumn` now throws `Unknown column label [nested.…]` instead. The
test's assertion is updated to match this message; behavior is unchanged.

### Changes to `UpdatingIntegrationTests`

**Convert from negative to positive** (one existing `Unsupported` test):

| Old test (Unsupported) | HQL | Key MQL assertion |
|------------------------|-----|-------------------|
| `testStructAggregateEmbeddablePathExpressionAssignment` | `update ItemWithNestedValue set nested.a = 0` | `$set: {"nested.a": 0}` |

**Add new positive test** (multiple struct fields in one statement → multiple `$set` keys):

| Test | HQL | Key MQL assertion |
|------|-----|-------------------|
| `testStructAggregateEmbeddableMultiFieldAssignment` | `update ItemWithPair set pair.a = 1, pair.b = 2` | `$set: {"pair.a": 1, "pair.b": 2}` |

Both test files already import `MongoAggregateSupport` solely to reference `UNSUPPORTED_MESSAGE_PREFIX` — that import is
removed along with the field.

### Changes to `DeletionIntegrationTests`

**Add new positive test** (struct-path filter in a DELETE — distinct statement path from UPDATE/SELECT, reuses the same
dot-notation field resolution):

| Test | HQL | Key MQL assertion |
|------|-----|-------------------|
| `testStructAggregateEmbeddablePathExpressionDeletion` | `delete from ItemWithNestedValue where nested.a = 0` | delete `q: {"nested.a": {$eq: 0}}` |

---

## Out of Scope

- Indexed array element access on `@Struct` array fields (e.g. `s.struct.items[0]`) — not supported; exact failure mode
  untested.
- Aggregate functions over struct fields (e.g. `COUNT(s.nested.a)`) — not in HIBERNATE-93 scope.

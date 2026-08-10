# HIBERNATE-224: Unwrap domain values in `@Struct` fields and HQL literals

## Summary

`ValueConversions.toBsonValue(Object)` dispatches on the runtime class of a value and throws
`SQLFeatureNotSupportedException` for anything it does not recognize. Its vocabulary is the JDBC-level
classes: `Boolean`, `Character`, `Integer`, `Long`, `Double`, `BigDecimal`, `String`, `byte[]`, `char[]`,
`ObjectId`, `Instant`, and arrays.

That vocabulary is the right one, because every write path reaches `ValueConversions` through
`BasicBinder.getBindValue`, which first unwraps the domain value to
`JdbcType.getPreferredJavaTypeClass(options)`. Two paths skip that step and pass the domain value straight
through, so a type whose domain class is outside the vocabulary works everywhere except in those two:

- `MongoStructJdbcType.createBindValue`, for the fields of a `@Struct` aggregate embeddable. It routes
  arrays through the binder and everything else directly.
- `AbstractMqlTranslator.visitQueryLiteral`, for an inlined HQL literal.

The fix is to unwrap in those two places too, through the same `ValueBinder` that binding a parameter of
that type already uses. The read side of the `@Struct` path has the mirror-image defect and needs the
inverse change.

The alternative fix, a `ValueConversions` branch per domain class, is rejected. It re-encodes mapping
decisions Hibernate's `JavaType` registry already owns, and it needs a new branch for every type anyone
ever adds.

This also promotes `java.time.Duration`, `java.time.Year`, `java.time.ZoneId`, `java.time.ZoneOffset`, and
`java.util.TimeZone` from accidentally working to supported: they are the types that already persist and
query correctly in every position except those two, and they appear in no documentation table.

## The defect

Measured on `main`, each of the five types persists and loads correctly as a top-level and as a plural
attribute, and works in a projection, a parameter predicate, `in`, `is null`, `order by`, and a mutation
`set`. Both failures below are identical for all five:

```
SQLFeatureNotSupportedException: Value [PT1M30.0000005S] of type [java.time.Duration] is not supported
    at ValueConversions.java:92
    at MongoStructJdbcType.createBindValue
```

```
Could not extract column [3] from JDBC ResultSet
  [Value [BsonString{value='Europe/Paris'}] of type [org.bson.BsonString]
   is not supported for the Java type [java.time.ZoneId]]
```

The defect is not confined to those five. It will block `java.time.OffsetDateTime` and
`java.time.ZonedDateTime` in the same two positions once they are supported, which is why HIBERNATE-225
depends on this work. That is not reproducible on `main`, since both types are rejected at boot by
`UNSUPPORTED_TYPES`; it was observed with the two temporarily removed from that set.

## Value mapping for the five types

The mapping is Hibernate's, not ours: each `JavaType` recommends a `JdbcType`, and that `JdbcType`'s
preferred Java type is what `ValueConversions` sees.

| Java type | Hibernate `JdbcType` | Preferred Java type | BSON |
|---|---|---|---|
| `java.time.Duration` | `DurationJdbcType` (a `NumericJdbcType`) | `BigDecimal` | `Decimal128`, nanoseconds |
| `java.time.Year` | `IntegerJdbcType` | `Integer` | `32-bit integer` |
| `java.time.ZoneId` | `VarcharJdbcType` | `String` | `String`, the zone ID |
| `java.time.ZoneOffset` | `VarcharJdbcType` | `String` | `String`, the offset ID |
| `java.util.TimeZone` | `VarcharJdbcType` | `String` | `String`, the zone ID |

## Implementation approach

`internal/type/MongoStructJdbcType.createBindValue`

Unwrap through the selectable's `ValueBinder` before calling `toBsonValue`. This absorbs the existing array
special case, whose body is already exactly that, so the array path becomes an instance of the general one
rather than a branch selected by JDBC type code.

Apply the unwrap only when the value is an instance of its selectable's mapped Java type, and otherwise keep
the existing direct call. The guard is load-bearing: the fallback is reachable for shapes we do not support,
notably a flattened `@Embeddable` nested inside a `@Struct` one, where `getValue` returns the whole
embeddable while the selectable's mapping is a scalar. Without it the unwrap fails with a bare
`ClassCastException` from `IntegerJavaType.unwrap` instead of the `SQLFeatureNotSupportedException` that
names the offending value, which is a regression against the failure contract in `ARCHITECTURE.md`.

`internal/type/MongoStructJdbcType.extractJdbcValues`

Read the JDBC-level value the binder would have written, then wrap it back through the mapped `JavaType`.
This is not symmetric in form with the write side. An ordinary column has `BasicExtractor.doExtract` do the
wrapping, but the `@Struct` read path has no `ValueExtractor`, so it wraps explicitly. A `JdbcType` reporting
no preferred Java type binds the domain value unchanged, so it is read back unchanged; `ObjectIdJdbcType` is
the case in the codebase today.

`internal/translate/AbstractMqlTranslator.visitQueryLiteral`

Unwrap the literal the same way, reaching the mapping through `QueryLiteral.getJdbcMapping()` and
`WrapperOptions` through `getSessionFactory().getWrapperOptions()`. The latter does not require an open
session, which matters because translation happens without one. A `SQLFeatureNotSupportedException` out of
the binder becomes a `FeatureNotSupportedException`, matching how the surrounding translator reports an
unsupported value.

`visitUnparsedNumericLiteral` has the same unguarded `toBsonValue` call but needs no change: its value is
always a `Number`, and every numeric type this extension supports is already in the `ValueConversions`
vocabulary.

`module-info.java`

Add the five types to the default type mapping table, per the mapping above.

## Supported and unsupported shapes

Positions are enumerated separately because each is a distinct translator or binder branch. "The five" is
`Duration`, `Year`, `ZoneId`, `ZoneOffset`, `TimeZone`.

| Shape | Before | After |
|---|---|---|
| The five as a basic attribute (round trip) | works, undocumented | documented and tested |
| The five as a plural attribute, array or `Collection` | works, undocumented | documented and tested |
| The five in a projection | works | unchanged |
| The five in a `where` comparison against a parameter | works | unchanged |
| The five in `in` and `is null` | works | unchanged |
| The five in `order by` | works | unchanged |
| The five in a mutation `set` | works | unchanged |
| The five inside a `@Struct` embeddable, write | `SQLFeatureNotSupportedException` | works |
| The five inside a `@Struct` embeddable, read | `SQLFeatureNotSupportedException` | works |
| The five inside a flattened `@Embeddable` | works | unchanged |
| The five as an HQL literal, in `where` and in a projection | `SQLFeatureNotSupportedException` | works |
| Any type with a `JavaType` and a standard `JdbcType`, in those two positions | fails unless `ValueConversions` happens to know its domain class | works |
| A flattened `@Embeddable` nested in a `@Struct` one | `SQLFeatureNotSupportedException` naming the value | unchanged, preserved by the mapped-type guard |
| A `JdbcType` reporting no preferred Java type, such as `ObjectIdJdbcType` | value passed through unchanged | unchanged |
| A numeric HQL literal | works | unchanged, `visitUnparsedNumericLiteral` untouched |

An HQL literal of an arbitrary type is written as a fully-qualified static field reference, the syntax
`ExpressionIntegrationTests.testStaticConstantOperand` already exercises. That is what makes the literal
position reachable from HQL for all five types.

No shape in this work is left unsupported, so no new `TODO-HIBERNATE-NNN` throw is introduced.

## Tests

| Test | Covers |
|---|---|
| `type/UnwrappedDomainTypeIntegrationTests`, parameterized over the five types | Every row of the shape table above for the five types, asserting exact BSON on the storage side and the exact MQL pipeline on the query side |
| `query/select/QueryLiteralIntegrationTests`, one case per supported scalar type | The change sits on a path every literal flows through, so types this work does not otherwise touch need explicit coverage. A static field reference is what produces a literal of an arbitrary type; numeric HQL literals such as `1L` take `visitUnparsedNumericLiteral` instead |
| `StructAggregateEmbeddableIntegrationTests` unchanged and passing | The `@Struct` write and read paths for every already-supported type, including the `Plural` record's scalars and the `ArraysAndCollections` entity's arrays. Its `Unsupported.testEmbeddable` is what catches removal of the mapped-type guard |

Every branch of the three changed methods is reached by the suite except the two `SQLException` catches in
`toBindValue`. Those exist because `ValueBinder.getBindValue` declares the checked exception; a
`BasicBinder` reports an unmappable value as a `HibernateException` from `JavaType.unwrap` rather than as a
`SQLException`, so no HQL input reaches them. They are kept rather than collapsed into a `fail`, because a
binder that does throw `SQLFeatureNotSupportedException` has to surface as `FeatureNotSupportedException`
and not as an `AssertionError`.

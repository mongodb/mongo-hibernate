# HIBERNATE-210: Row-value (tuple) predicates — design

## Summary

Support HQL row-value constructor predicates over multiple columns — `(a, b) = (…)`, `(a, b) <> (…)`,
`(a, b) in ((…), (…))` — in **both** filter position (`where`) and expression position (`select` / `$expr`), so
the WHERE/SELECT parity the codebase maintains holds for row values too. Values may be bound parameters or
literals. Out of scope (HIBERNATE-211): lexicographic ordering comparison (`>`, `<`, `>=`, `<=`).

This is a precursor to HIBERNATE-207 (composite primary key), which reuses this decomposition for id predicates.
Both positions were verified end-to-end by a spike.

## MongoDB mechanism

A row-value predicate over N columns decomposes to per-component boolean logic. The rendering differs by position.

Filter position (`$match`):

```
(a, b) =  (v1, v2)      → { $and: [ { a: {$eq: v1} }, { b: {$eq: v2} } ] }
(a, b) <> (v1, v2)      → { $nor: [ { $and: [ … ] } ] }
(a, b) in (r1, r2)      → { $or:  [ { $and: [ …r1 ] }, { $and: [ …r2 ] } ] }
```

Expression position (`$expr` — e.g. a `select`ed comparison), aggregation form:

```
(a, b) =  (v1, v2)      → { $and: [ {$eq: ["$a", v1]}, {$eq: ["$b", v2]} ] }
(a, b) <> (v1, v2)      → { $not: [ { $and: [ … ] } ] }
(a, b) in (r1, r2)      → { $or:  [ { $and: [ … ] }, { $and: [ … ] } ] }
```

A single-row `in` collapses to the `$and`. Null components follow MQL semantics (per-component `$eq`); there is no
SQL three-valued emulation — consistent with the project's null-semantics stance. So `<>` returns rows where a
component is null.

The compact filter forms above apply when every component compares a field to a value — in **either order**. The
field side is detected per component, so value-on-left is compact too (`=`/`<>` are symmetric):

```
where (v1, v2) = (a, b)  → { $and: [ { a: {$eq: v1} }, { b: {$eq: v2} } ] }
```

A **field-to-field** tuple comparison — `where (a, b) = (c, d)` where `c`, `d` are columns — has no value side, so
the compact `{field: {$eq: value}}` form cannot apply (`$eq` against another field needs an aggregation
expression). It renders through `$expr`, reusing the expression-position decomposition — mirroring the scalar
comparison path, which likewise routes field-to-field through `$expr`:

```
where (a, b) =  (c, d)  → { $expr: { $and: [ {$eq: ["$a", "$c"]}, {$eq: ["$b", "$d"]} ] } }
where (a, b) <> (c, d)  → { $expr: { $not: [ { $and: [ … ] } ] } }
```

## Pipeline structure

No new stages and no change to stage order. A filter-position predicate renders inside the `$match` it belongs to;
an expression-position predicate renders inside the `$project`/`$expr` context where the comparison appears.

## Implementation approach

No new AST node classes. Filter forms reuse `AstLogicalFilter` (`$and`/`$or`/`$nor`), `AstFieldOperationFilter`,
`AstComparisonFilterOperation`; expression forms reuse `AstLogicalOperatorExpression` (`$and`/`$or`/`$not`) and
`AstBinaryOperatorExpression` (`$eq`).

Translator — `internal/translate/AbstractMqlTranslator`, four points (two positions × comparison / `IN`):

- `toFilter` (comparison, filter): `SqlTuple` operands → per-component `$and` for `EQUAL`, `$nor` of that `$and`
  for `NOT EQUAL`, **when every component is field-vs-value in either order** (the field side is detected per
  component, so value-on-left is compact too); otherwise (field-to-field, value-to-value) route the whole
  comparison through `new AstExprFilter(toComparisonExpression(...))` — the `$expr` form — mirroring the scalar
  path's `isComparingFieldWithValue` fork.
- `visitInListPredicate` filter branch: `SqlTuple` test expression → `$or` of per-component `$and` (single-row →
  `$and`); negated wraps in `$nor`.
- `toComparisonExpression` (comparison, expression): `SqlTuple` operands → aggregation `$and` for `EQUAL`, `$not`
  for `NOT EQUAL`. Its return type widens from `AstBinaryOperatorExpression` to `AstExpression`.
- `visitInListPredicate` expression branch: `SqlTuple` test expression → aggregation `$or` of per-component
  `$and`; negated wraps in `$not`.

An ordering comparison (`>`, `<`, `>=`, `<=`) throws `FeatureNotSupportedException` (`TODO-HIBERNATE-211`) in
both `toFilter` and `toComparisonExpression`.

Today `acceptAndYieldExpression` rejects a bare `SqlTuple` ("Expression not supported: SqlTuple") — that is the
current failure for both positions; after this change the tuple operands are consumed by the comparison / `IN`
handlers before they would reach that guard.

## Supported and unsupported shapes

| Shape | |
|---|---|
| `(a, b) = (…)` / `<> (…)` — parameters or literals, filter and expression position | ✅ |
| `where (a, b) = (c, d)` — field-to-field comparison → `$expr` in `$match` | ✅ |
| `(a, b) in ((…), (…))` — multi-row and single-row, filter and expression position | ✅ |
| Ordering comparison `(a, b) > (…)` / `<` / `>=` / `<=` (lexicographic) | ❌ HIBERNATE-211 |

## Tests

A dedicated `RowValuePredicateIntegrationTests` over a plain multi-column entity, following the codebase
convention (positive cases as outer-level `@Test` methods; a `@Nested class Unsupported` for negatives). Each
positive asserts the full MQL, the full result set, and the affected collections.

Positive:

| Test | Covers |
|---|---|
| `where (a, b) = (:p1, :p2)` | filter `=` → `$and` |
| `where (a, b) = (1, 2)` (literals) | filter literal row-value → same path |
| `where (a, b) <> (:p1, :p2)` | filter `<>` → `$nor` |
| `where (a, b) = (c, d)` (field-to-field) | filter `=` → `$expr` `$and` of `$eq` |
| `where (a, b) <> (c, d)` (field-to-field) | filter `<>` → `$expr` `$not` |
| `where (v1, v2) = (a, b)` (value-on-left) | filter → compact `$and` (field side detected per component) |
| `where (a, b) in ((…), (…))` (≥ 2 rows) | filter `in` → `$or`-of-`$and` |
| `where (a, b) in ((…))` (1 row) | filter single-row `in` → `$and` |
| `where (a, b) in ((c, d))` (field rows) | filter `in` → `$expr` |
| `where (a, b) not in ((…), (…))` | filter negated `in` → `$nor` |
| `select (a, b) = (1, 2)` | expression `=` → agg `$and` of `$eq` |
| `select (a, b) <> (1, 2)` | expression `<>` → agg `$not` |
| `select (a, b) in ((…), (…))` | expression `in` → agg `$or`-of-`$and` |
| `select (a, b) not in ((…), (…))` | expression negated `in` → agg `$not` |

Negative (`@Nested class Unsupported`, `FeatureNotSupportedException`, `TODO-HIBERNATE-211`):

| Test | |
|---|---|
| `where (a, b) > (:p1, :p2)` | ordering comparison, filter position |
| `select (a, b) > (1, 2)` | ordering comparison, expression position |

These cover every non-trivial path: filter and expression branches of both the comparison and `IN` handlers,
single- and multi-row `in`, and the ordering throw in both positions.

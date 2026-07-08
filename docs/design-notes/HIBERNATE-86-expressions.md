# Expressions in SELECT and WHERE (HIBERNATE-86)

Translating computed expressions in SELECT projections and WHERE predicates to MongoDB aggregation expressions: arithmetic operators (`+ - * / div`, unary `+/-`), comparison operators (`>`, `>=`, `<`, `<=`, `=`, `<>`), and the scalar predicate family (AND/OR junctions, NOT, IN, LIKE, IS NULL, boolean fields) as value-producing expressions.

## MongoDB Mechanism

### SELECT — `$project`

Aggregation expression operators appear directly as field values in `$project`. No `$expr` wrapper needed.

```javascript
{ $project: { "#c_1": { $add: ["$x", 1] } } }          // select x + 1
{ $project: { "total": { $add: ["$x", 1] } } }          // select x + 1 as total
{ $project: { "#c_1": { $add: [{ $multiply: ["$x", "$y"] }, 1] } } }  // select x * y + 1
{ $project: { "#c_1": { $toInt: { $divide: ["$x", "$y"] } } } }       // select x div y
{ $project: { "#c_1": { $gt: ["$x", 1] } } }            // select x > 1
```

### WHERE — `$expr` in `$match`

`$expr` is required when either side of a comparison is not a simple value. Each predicate independently decides; AND/OR junctions compose naturally.

```javascript
{ $match: { $expr: { $gt: [{ $add: ["$x", 1] }, 5] } } }   // where x + 1 > 5
{ $match: { $expr: { $gt: ["$x", "$y"] } } }                // where x > y
{ $match: { $and: [{ $expr: { $gt: [...] } }, { y: { $eq: 3 } }] } }  // mixed AND
```

No structural pipeline change — new expressions slot into the existing `$match` and `$project` stages.

## Key Decisions

**Expressions translate through the same visitor dispatch as everything else.** Expressions appear in two contexts — SELECT (`$project`) and WHERE (`$expr`) — but both go through Hibernate's `SqlAstWalker` visitor, like the rest of the translator, rather than a separate recursive method. A caller requests an expression with `acceptAndYield(node, EXPRESSION)`; each node's `visitXxx` yields an `AstExpression`, recursing into its operands the same way. This keeps one translation path per node type instead of a parallel `instanceof` cascade that would re-derive (and drift from) the field-path, formula, and elemMatch handling the real visitor methods already enforce. Nodes that render differently by context — a column reference is a bare field path in a filter but a wrapped field-path expression inside `$expr`/`$project`; a literal or parameter is a document value or an expression — inspect which descriptor is expected and yield the matching form.

**A value is not an expression; it is converted to one.** A literal or parameter renders one way in document-value position and can render differently in aggregation-expression position — a string beginning with `$` is a field path there, and a document/array is an operator invocation, so such a value must be wrapped in `{$literal: …}`. `AstValue` therefore does **not** extend `AstExpression`; a value used as an expression goes through `AstValueExpression`, which applies `$literal` when the value would otherwise be misread (a `$`-string, document, or array). Treating every value as an expression would silently mistranslate, e.g. `select name = '$foo'` → `{$eq: ["$name", "$foo"]}`, where `"$foo"` is read as a field reference.

**Projection key for computed values.** An AS alias (e.g. `select x + 1 as total`) is used as the `$project` key when present. Without an alias the key is `#c_N`, where `#` is a character the translator rejects in mapped field names — ensuring no collision with mapped entity fields.

**`$expr` decision is per-predicate.** A comparison predicate switches to `$expr` only when at least one operand is not a simple field-or-value. Plain field comparisons continue to use the existing `{field: {$op: value}}` form. This keeps the common case simple.

**Integer division truncates the quotient.** MongoDB's `$divide` always returns a double, but Hibernate infers an integer result type for integer operands and reads the column back as that integer. Integer division (`x / y`, `x div y`, or `CriteriaBuilder.quot` on integers) is therefore wrapped in a truncation operator chosen by the result type — `$toLong` for a 64-bit `BIGINT` result, `$toInt` for narrower integral types — while a floating operand keeps a plain `$divide`. Using `$toInt` for a `long` result would overflow and fail to read back as 64-bit. One rule covers `DIVIDE`, `QUOT`, and `DIVIDE_PORTABLE`, independent of the `PORTABLE_INTEGER_DIVISION` setting.

**The Criteria API reaches operators HQL doesn't.** Criteria is a supported entry point, and it produces two arithmetic operators HQL never emits: `CriteriaBuilder.quot` → `QUOT` (behaviorally identical to `DIVIDE` — both render `/` and infer their result type the same way; the type resolver ignores the operator) and `CriteriaBuilder.mod` → the `MODULO` operator → `$mod`. HQL's `%`, by contrast, is rewritten to a `mod()` function call — not the `MODULO` operator — and remains unsupported (HIBERNATE-196). So "unreachable from HQL" is not a safe reason to omit an operator: `QUOT` and `MODULO` reach the translator through Criteria.

**Predicates are also expressions.** Every predicate the translator supports as a `$match` filter (AND/OR junctions, NOT, grouped, boolean field, IN list, LIKE, IS NULL) also renders as a value-producing aggregation expression, so it works in a SELECT projection and as a nested operand — not only as a top-level filter. This removes the asymmetry where `x > 1 and y < 2` filtered in WHERE but threw in SELECT. Logical connectives use a dedicated `AstLogicalOperatorExpression` over an `AstLogicalOperator` enum (`$and`/`$or`/`$not`), mirroring `AstLogicalFilter`/`AstLogicalFilterOperator` on the filter side; IN and LIKE get `AstInExpression` (`$in`) and `AstRegexMatchExpression` (`$regexMatch`). IS NULL and boolean-field predicates are genuine equalities, so they reuse `AstBinaryOperatorExpression` with `$eq`/`$ne` against a wrapped `null`/`true`/`false` — not false sharing, but what those predicates mean. Every value operand (IN options, the `null`/`true`/`false`) is routed through `AstValueExpression`.

**Negation differs by context.** The aggregation-expression form of NOT (and negated IN/LIKE) wraps the operand in `$not` — a one-element array — because that is the aggregation `$not`. The filter form uses `$nor`, because the query `$not` is field-scoped and cannot negate a whole filter. The two forms are chosen by which descriptor the visitor is yielding, so the same predicate node produces the correct shape in each position.

**NOT and boolean fields reach the expression form only as operands.** A bare `select not (x > 1)` compiles the `not` to a `not()` function call (HIBERNATE-196), and a bare `select flag` selects the boolean column as a plain field rather than a `BooleanExpressionPredicate`. So NOT and boolean-field predicates render as `$not`/`$eq` expressions only when they appear as an operand of another supported predicate — e.g. `select (x > 1) and not (y < 5)`, `select (x > 1) and flag`. Comparison, IN, LIKE, and IS NULL are reachable directly as select items.

## Supported and Unsupported Shapes

### SELECT clause

| Expression | MQL | Status |
|---|---|---|
| `x + y`, `x - y`, `x * y` | `{$add: […]}`, `{$subtract: […]}`, `{$multiply: […]}` | ✅ |
| `x / y`, `x div y` (integer operands) | `{$toInt/$toLong: {$divide: […]}}` (truncating; `$toLong` for `long`) | ✅ |
| `x / 2.0` (floating operand) | `{$divide: […]}` | ✅ |
| `CriteriaBuilder.quot(x, y)` | same as `x / y` (`QUOT` = `DIVIDE`) | ✅ |
| `CriteriaBuilder.mod(x, y)` | `{$mod: […]}` | ✅ |
| `x * y + 1` (nested) | `{$add: [{$multiply: […]}, 1]}` | ✅ |
| `x + 1 as total` | key `"total"` in `$project` | ✅ |
| `:p + x` (parameter) | `{$add: [param, "$x"]}` | ✅ |
| `-x` | `{$multiply: [-1, "$x"]}` | ✅ |
| `x > 1` (comparison in SELECT) | `{$gt: ["$x", 1]}` | ✅ |
| `x > 1 and y < 2`, `… or …` | `{$and: […]}`, `{$or: […]}` | ✅ |
| `… and not (y < 5)` (NOT as operand) | `{$not: [{$lt: […]}]}` | ✅ |
| `… and flag` (boolean field as operand) | `{$eq: ["$flag", true]}` | ✅ |
| `x in (1, 2, 3)`, `x not in (…)` | `{$in: ["$x", […]]}`, negated `{$not: […]}` | ✅ |
| `name like 'a%'`, `not like` | `{$regexMatch: {…}}`, negated `{$not: […]}` | ✅ |
| `x is null`, `x is not null` | `{$eq: ["$x", null]}`, `{$ne: …}` | ✅ |
| `(x + 1) is null` (computed operand) | `{$eq: [{$add: […]}, null]}` | ✅ |
| `x % 3`, `mod(x, y)` (HQL) | HQL emits a `mod()` function call | ❌ HIBERNATE-196 |
| `abs(x) + 1` (function call) | — | ❌ HIBERNATE-196 |
| `case when … end` | — | ❌ HIBERNATE-83 |
| `x between 1 and 5` | — | ❌ not supported as a filter either |
| `exists (…)` | — | ❌ match-only (`$elemMatch`) |

### WHERE clause

| Expression | MQL | Status |
|---|---|---|
| `x + 1 > 5` | `{$expr: {$gt: [{$add:[…]}, 5]}}` | ✅ |
| `x > y` (field vs field) | `{$expr: {$gt: ["$x","$y"]}}` | ✅ |
| `x + 1 > 5 and y = 3` | mixed `$and` | ✅ |
| `x + 1 = y + 2` | both sides computed | ✅ |
| `-x > 5` | `{$expr: {$gt: [{$multiply:[-1,"$x"]}, 5]}}` | ✅ |
| `(x + 1) is null` | `{$expr: {$eq: [{$add:[…]}, null]}}` | ✅ |
| `case when … end > 3` | — | ❌ HIBERNATE-83 |

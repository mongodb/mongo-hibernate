# Expressions in SELECT and WHERE (HIBERNATE-86)

> **Superseded in part.** This spec covers the arithmetic/comparison effort. The scalar predicate
> family (AND/OR/NOT/IN/LIKE/IS NULL, including `IS NULL` on a computed operand) was added afterward —
> see `2026-07-08-predicate-expressions-design.md`. Rows below that were later superseded are marked
> inline. The durable overview is the design note `docs/design-notes/HIBERNATE-86-expressions.md`.

## MongoDB Mechanism

### SELECT — computed fields in `$project`

Aggregation expression operators appear directly as field values in `$project`. No `$expr` wrapper needed.

```javascript
// select x + 1 from Foo  (no alias — generated key)
{ $project: { "#c_1": { $add: ["$x", 1] } } }

// select x + 1 as total from Foo  (AS alias used as key)
{ $project: { "total": { $add: ["$x", 1] } } }

// select x * y + 1 from Foo  (nested)
{ $project: { "#c_1": { $add: [{ $multiply: ["$x", "$y"] }, 1] } } }

// select 10 div x from Foo  (integer division)
{ $project: { "#c_1": { $toInt: { $divide: [10, "$x"] } } } }
```

### WHERE — `$expr` in `$match`

`$expr` is required when one or both sides of a comparison are not a simple field-vs-value. Each predicate independently decides whether it needs `$expr`; AND/OR junctions combine them naturally.

```javascript
// from Foo where x + 1 > 5
{ $match: { $expr: { $gt: [{ $add: ["$x", 1] }, 5] } } }

// from Foo where x > y  (field vs field)
{ $match: { $expr: { $gt: ["$x", "$y"] } } }

// from Foo where x + 1 > 5 and y = 3  (mixed — only one predicate needs $expr)
{ $match: { $and: [{ $expr: { $gt: [{ $add: ["$x", 1] }, 5] } }, { y: { $eq: 3 } }] } }
```

## Pipeline Structure

No structural change. `$project` stays last; `$match` stays before `$sort`/`$skip`/`$limit`/`$project`. The new nodes slot into existing stages.

## Implementation Approach

### New AST nodes

**`AstExpression` interface** — rendering contract: write a BSON value in value position (after a field name or inside an array). All nodes below implement it.

**`AstFieldPathExpression(String fieldPath)`** — renders `"$fieldPath"` (a string). Represents a field reference inside an aggregation expression.

**`AstBinaryOperatorExpression(String operator, AstExpression left, AstExpression right)`** — renders `{ $operator: [left, right] }`. Always binary: Hibernate's `BinaryArithmeticExpression` is a binary node, so `x + y + 1` produces two nested `AstBinaryOperatorExpression` nodes, not a 3-element array. Covers `$add`, `$subtract`, `$multiply`, `$divide`, `$mod`, and aggregation comparison operators (`$gt`, `$gte`, `$lt`, `$lte`, `$eq`, `$ne`).

**`AstUnaryOperatorExpression(String operator, AstExpression operand)`** — renders `{ $operator: <operand> }` (no array). Used for the `$toInt`/`$toLong` truncation wrapping integer division.

**`AstProjectStageExpressionSpecification(String key, AstExpression expression)`** — renders `"key": <expression>` in the `$project` document. Implements `AstProjectStageSpecification`.

**`AstExprFilter(AstExpression expression)`** — renders `{ $expr: <expression> }`. Implements `AstFilter`.

**`AstValueExpression(AstValue value, boolean literalWrapped)`** — renders a value in aggregation-expression position, wrapping it in `{ $literal: value }` when `literalWrapped`. `AstValue` does **not** extend `AstExpression`: a value and an expression do not always render the same (a `$`-prefixed string is a field path in expression position, a document/array is an operator invocation), so a value used as an expression is converted explicitly through this node — with `$literal` applied for a `$`-string, document, or array — rather than shared by subtyping.

### Changes to `AbstractMqlTranslator`

Expressions are translated through the same visitor/yield dispatch as the rest of the translator — there is no separate recursive method. A caller requests an expression with `acceptAndYield(node, EXPRESSION)`; the node's `visitXxx` yields an `AstExpression` and recurses into its operands the same way.

**New `EXPRESSION` descriptor** in `AstVisitorValueDescriptor` typed to `AstExpression`.

**`AstVisitorValueHolder.expects(descriptor)`** — reports which descriptor the current `acceptAndYield` is waiting for. Nodes that render differently by context branch on it. Two helpers keep the branching DRY:
- `yieldValueOrExpression(AstValue, boolean literalWrapped)` — under `EXPRESSION` yields an `AstValueExpression` (applying `$literal` per `literalWrapped`), else the bare `AstValue` under `VALUE` (used by `visitQueryLiteral`, `visitUnparsedNumericLiteral`, `visitParameter`). The caller computes `literalWrapped` from the literal's `BsonValue` or the parameter's declared type.
- `yieldFieldPathOrExpression(String)` — yields `new AstFieldPathExpression(path)` under `EXPRESSION`, else the bare path `String` under `FIELD_PATH` (used by `visitColumnReference`, after its formula and elemMatch guards).

**`visitSelectClause`** — existing `ColumnReference` branch unchanged. Replace the `throw` with a general `else` branch:
```java
} else {
    var key = resolveProjectionKey(sqlSelection);
    spec = new AstProjectStageExpressionSpecification(
            key, acceptAndYield(sqlSelection.getExpression(), EXPRESSION));
}
```

**`visitBinaryArithmeticExpression`** — recurse on both operands via `acceptAndYieldExpression(operand)`, then yield `EXPRESSION`: `ADD/SUBTRACT/MULTIPLY` → `AstBinaryOperatorExpression`; `MODULO` → `$mod`; `DIVIDE/QUOT/DIVIDE_PORTABLE` → `$divide`, wrapped in a truncation operator when the result type is integral — `$toLong` for `BIGINT`, `$toInt` for narrower integral types — plain `$divide` for a floating result. `QUOT` (`CriteriaBuilder.quot`) is identical to `DIVIDE`; `MODULO` reaches the translator via `CriteriaBuilder.mod` (HQL's `%` becomes a `mod()` function call instead — HIBERNATE-196).

**`acceptAndYieldExpression(Expression)`** — the guarded entry point for a position that requires an aggregation expression (computed `$project` field, `$expr` operand). Rejects operands that cannot render as an expression with `FeatureNotSupportedException`, rather than letting them trip the holder's descriptor assertion. *(In this spec's scope that was tuples and every predicate but `ComparisonPredicate`; the predicate-family effort narrowed the guard to `SqlTuple` and `ExistsPredicate` — the only nodes that yield a non-`EXPRESSION` descriptor without throwing on their own.)* Used by `visitSelectClause`, `toComparisonExpression`, and the arithmetic/unary operand recursion.

**`visitUnaryOperationExpression`** — recurse on the operand via `acceptAndYieldExpression`, then yield `EXPRESSION`: `UNARY_MINUS` → `AstBinaryOperatorExpression("$multiply", AstValueExpression(AstLiteral(-1), false), operand)`; `UNARY_PLUS` → the operand itself (identity).

**Terminal (unsupported) expression cases live in each node's own visit method**, reachable uniformly from every clause:
- `visitCaseSearchedExpression` / `visitCaseSimpleExpression` → throw HIBERNATE-83.
- `visitSelfRenderingExpression` → when an `EXPRESSION` is expected (a function call used as an operand), throw HIBERNATE-196; otherwise delegate to the node's own rendering (this is the path supported functions such as the array operators use).
- Any other expression type reaches its own `visitXxx`, which throws `FeatureNotSupportedException` as before.

**`visitRelationalPredicate`** — a binary split between value and filter contexts:
```java
public void visitRelationalPredicate(ComparisonPredicate comparisonPredicate) {
    // comparison used as a value (e.g. `select x > 1`) → a bare aggregation expression;
    // otherwise it is a filter
    if (astVisitorValueHolder.expects(EXPRESSION)) {
        astVisitorValueHolder.yield(EXPRESSION, toComparisonExpression(comparisonPredicate));
    } else {
        astVisitorValueHolder.yield(FILTER, toFilter(comparisonPredicate));
    }
}
```
`toFilter` holds the filter sub-decision: a field-vs-value comparison uses the compact `{field: {$op: value}}` form (via `toFieldValueFilter`); anything else wraps the comparison expression in `$expr`.
```java
private AstFilter toFilter(ComparisonPredicate comparisonPredicate) {
    return isComparingFieldWithValue(comparisonPredicate)
            ? toFieldValueFilter(comparisonPredicate)
            : new AstExprFilter(toComparisonExpression(comparisonPredicate));
}
```
`toComparisonExpression` recurses on both sides via `acceptAndYield(…, EXPRESSION)` and builds an `AstBinaryOperatorExpression`; it is shared by the value-expression and `$expr` cases. `toFieldValueFilter` holds the compact-form logic (field-side detection and operator inversion). In the `$expr` and value forms operator direction is preserved as-is — both sides are explicit.

**Projection key resolution** — `visitSelectStatement` builds a `Map<Integer, String>` (0-based `valuesArrayPosition` → alias) from `selectStatement.getDomainResultDescriptors()` using `DomainResult.getResultVariable()` and `collectValueIndexesToCache(BitSet)`. Stored in a `@Nullable` field on `AbstractMqlTranslator`. `resolveProjectionKey(SqlSelection)` looks up `sqlSelection.getValuesArrayPosition()`; falls back to `"#c_" + sqlSelection.getJdbcResultSetIndex()` (the `#` prefix is blocked in mapped field names, preventing collisions).

### Change to `MongoStatement`

**`isExcludeProjectSpecification`** — currently throws for document-valued `$project` specs. Add a check: if the value is a `BsonDocument`, it is an aggregation expression and is treated as an inclusion (return `false`).

## Supported and Unsupported Shapes

### SELECT clause

| Expression | MQL | Status |
|---|---|---|
| `x + 1` | `{$add: ["$x", 1]}` | ✅ |
| `x - 1` | `{$subtract: ["$x", 1]}` | ✅ |
| `x * 2` | `{$multiply: ["$x", 2]}` | ✅ |
| `x / 2.0` (floating operand) | `{$divide: ["$x", 2.0]}` | ✅ |
| `x / y`, `x div y` (integer operands) | `{$toInt/$toLong: {$divide: ["$x", "$y"]}}` (truncating; `$toLong` for `long`) | ✅ |
| `CriteriaBuilder.quot(x, y)` | same as `x / y` (`QUOT` = `DIVIDE`) | ✅ |
| `CriteriaBuilder.mod(x, y)` | `{$mod: ["$x", "$y"]}` | ✅ |
| `x % 3`, `mod(x, y)` (HQL) | HQL emits a `mod()` function call, not the `MODULO` operator | ❌ HIBERNATE-196 |
| `x * y + 1` (nested) | `{$add: [{$multiply: ["$x","$y"]}, 1]}` | ✅ |
| `x + 1 as total` | key `"total"` in `$project` | ✅ |
| `?1 + x` (parameter) | `{$add: [param, "$x"]}` | ✅ |
| `-x` (unary negation) | `{$multiply: [-1, "$x"]}` | ✅ |
| `+x` (unary plus) | `"$x"` (identity) | ✅ |
| `x > 1` (comparison in SELECT) | `{$gt: ["$x", 1]}` | ✅ |
| function call operand | — | ❌ HIBERNATE-196 |

### WHERE clause (via `$expr`)

| Expression | MQL | Status |
|---|---|---|
| `x + 1 > 5` | `{$expr: {$gt: [{$add:[…]}, 5]}}` | ✅ |
| `x > y` (field vs field) | `{$expr: {$gt: ["$x","$y"]}}` | ✅ |
| `x + 1 > 5 AND y = 3` | mixed `$and` | ✅ |
| `x + 1 = y + 2` | both sides computed | ✅ |
| `(x + 1) IS NULL` | `{$expr: {$eq: [{$add:[…]}, null]}}` | ✅ *(predicate-family effort)* |
| `-x > 5` (unary negation operand) | `{$expr: {$gt: [{$multiply:[-1,"$x"]}, 5]}}` | ✅ |

## Tests

All tests live in a new `ExpressionIntegrationTests` integration test class.

### Positive (in `@Nested class Positive`, `@BeforeEach` seeds data)

| Test | What it covers |
|---|---|
| `select x + 1 from Foo` | ADD, literal RHS, generated projection key |
| `select x - 1 from Foo` | SUBTRACT |
| `select x * 2 from Foo` | MULTIPLY |
| `select x / 2.0 from Foo` | DIVIDE with a floating operand → plain `$divide` |
| `select x / y from Foo` (int) | integer DIVIDE → `$toInt($divide)`, portable division off |
| `select a / b from Foo` (long) | BIGINT DIVIDE → `$toLong($divide)` (own `@Nested class LongDivision`) |
| `select x / y from Foo` with `PORTABLE_INTEGER_DIVISION=true` | DIVIDE_PORTABLE → `$toInt($divide)` (own `@Nested` class with separate `@ServiceRegistry`) |
| `CriteriaBuilder.quot(x, y)` (Criteria) | QUOT integer division → `$toInt($divide)` (own `@Nested class CriteriaApi`) |
| `CriteriaBuilder.mod(x, y)` (Criteria) | MODULO operator → `$mod` (own `@Nested class CriteriaApi`) |
| `select x * y + 1 from Foo` | nested arithmetic |
| `select x + 1 as total from Foo` | AS alias used as projection key |
| `select ?1 + x from Foo` | JdbcParameter operand |
| `select x, x + 1 from Foo` | mixed column + computed in same clause |
| `from Foo where x + 1 > 5` | arithmetic in WHERE, `$expr` |
| `from Foo where x > y` | field vs field, `$expr` |
| `from Foo where x + 1 > 5 and y = 3` | mixed AND: one `$expr`, one regular |
| `from Foo where x + 1 = y + 2` | both sides computed |
| `select x > 1 from Foo` (and `<>`, `<`, `<=`, `>=`) | comparison expression in SELECT, each `$expr` operator |

### Negative (outer class or `@Nested class Unsupported`)

❌ shapes: HIBERNATE-196 (function call as operand). *(`IS NULL` on a computed operand and boolean
junctions in expression position — both listed as ❌ in earlier drafts of this spec — are now
supported by the predicate-family effort; see `2026-07-08-predicate-expressions-design.md`.)*

| Test | What it covers |
|---|---|
| `select case when x > 5 then 1 else 0 end from Foo` | CASE in SELECT throws HIBERNATE-83 |
| `select abs(x) + 1 from Foo` | function call as operand throws HIBERNATE-196 |
| `select x % 3 from Foo` | HQL `%` (a `mod()` function call) throws HIBERNATE-196 |
| `from Foo where case when x > 5 then 1 else 0 end > 3` | CASE in WHERE throws HIBERNATE-83 |

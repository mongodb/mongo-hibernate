# Predicates as Expressions in SELECT and WHERE (HIBERNATE-86)

Extends the arithmetic/comparison expression support so that **predicates already supported as `$match`
filters also work as value-producing aggregation expressions** — in SELECT projections and as `$expr`
operands. This removes the asymmetry where `x > 1 and y < 2` works in WHERE but throws in SELECT.

## Scope

**In scope** — every predicate the translator supports as a `$match` filter today:

| Predicate | HQL |
|---|---|
| comparison | `x > 1` — **already done** (`visitRelationalPredicate` EXPRESSION branch) |
| AND / OR (`Junction`) | `x > 1 and y < 2` |
| NOT (`NegatedPredicate`) | `not (x > 1)` |
| grouped (parentheses) | `(x > 1)` |
| boolean field (`BooleanExpressionPredicate`) | `flag` |
| IN list (`InListPredicate`) | `x in (1, 2, 3)` |
| LIKE (`LikePredicate`) | `name like 'a%'` |
| IS NULL (`NullnessPredicate`) | `x is null` |

**Out of scope** — not supported as a `$match` filter today either (all throw), tracked elsewhere:

- `BETWEEN` (`visitBetweenPredicate` throws; not desugared by HQL) — not supported in filters, so not here.
- IN-subquery, IN-array — throw in filters.
- `EXISTS`-over-unnest — `$elemMatch`, a `$match`-only construct with no scalar `$expr` form; stays
  filter-only and is already locked by `ExistsSelectQueryIntegrationTests.testExistsInSelectIsUnsupported`.

**Null/missing semantics** are governed by HIBERNATE-183 (a required property, MQL semantics only). Both a
`$match` filter and its `$expr` twin follow MQL semantics by contract, so there is nothing to reconcile.

**HQL-shape caveat for NOT and boolean fields.** A predicate reaches these expression branches only when HQL
parses it *as a predicate* in a value/operand position. Two spellings do not: a bare `select not (x > 1)`
compiles the `not` to a `not()` *function* call (→ HIBERNATE-196), and a bare `select flag` selects the
boolean column as a plain field, not a `BooleanExpressionPredicate`. NOT and boolean-field reach the
`$not`/`$eq` expression forms when they appear as an operand of a supported predicate expression — e.g.
`select (x > 1) and not (y < 5)`, `select (x > 1) and flag`. Comparison, IN, LIKE, and IS NULL are reachable
directly as `select` items.

## MongoDB Mechanism

Each predicate has an aggregation-expression counterpart that renders in value position (a `$project`
field value or an operand), exactly like the arithmetic/comparison expressions already supported.

```javascript
// select x > 1 and y < 2        →
{ $project: { "#c_1": { $and: [ { $gt: ["$x", 1] }, { $lt: ["$y", 2] } ] } } }

// select (x > 1) and not (y < 5)   →  (NOT as a junction operand — see caveat above)
{ $project: { "#c_1": { $and: [ { $gt: ["$x", 1] }, { $not: [ { $lt: ["$y", 5] } ] } ] } } }

// select x in (1, 2, 3)         →
{ $project: { "#c_1": { $in: ["$x", [1, 2, 3]] } } }

// select x is null              →
{ $project: { "#c_1": { $eq: ["$x", null] } } }        // $ne for `is not null`

// select name like 'a%'         →  (HQL `like` is case-sensitive → options "s")
{ $project: { "#c_1": { $regexMatch: { input: "$name", regex: "^a.*$", options: "s" } } } }

// select (x > 1) and flag       →  (boolean field as a junction operand — see caveat above)
{ $project: { "#c_1": { $and: [ { $gt: ["$x", 1] }, { $eq: ["$flag", true] } ] } } }

// select (x + 1) is null      →  computed operand routed through the expression form
{ $project: { "#c_1": { $eq: [ { $add: ["$x", 1] }, null ] } } }
```

Operator forms: `$and`/`$or` take an array of expressions; `$not` takes a one-element array; `$in` takes
`[value, arrayExpr]`; `$regexMatch` is a document `{input, regex, options}`. Negated `IN`/`LIKE` wrap the
positive form in `$not`.

## Pipeline Structure

No structural change. These slot into existing `$project` field values and `$expr` operands, alongside the
arithmetic/comparison expressions. The `$match` → `$sort` → `$skip/$limit` → `$project` order is untouched.

## Implementation Approach

### New AST expression nodes

Each construct gets a node that reflects what it *is*, not one shared because the BSON happens to match
(the lesson from `AstValue`/`AstExpression`). Value operands are converted through `AstValueExpression`, so
they are `$literal`-wrapped when needed.

- **`AstLogicalOperatorExpression(AstLogicalOperator operator, List<AstExpression> operands)`** — renders
  `{ $operator: [op0, op1, …] }` for the logical connectives via a new `AstLogicalOperator` enum
  (`AND`/`OR`/`NOT`, `$not` gets a one-element list), mirroring `AstLogicalFilter`/`AstLogicalFilterOperator`
  on the filter side. (For symmetry, `AbstractMqlTranslator` references both enums qualified —
  `AstLogicalOperator.AND`, `AstLogicalFilterOperator.NOR` — rather than static-importing either.)
- **`AstInExpression(AstExpression value, List<AstExpression> options)`** — renders
  `{ $in: [value, [opt0, opt1, …]] }`. Self-contained (the options array is rendered inline); membership
  is its own construct, not a binary operator, so it is not forced into `AstBinaryOperatorExpression`.
- **`AstRegexMatchExpression(AstExpression input, String regex, String options)`** — renders
  `{ $regexMatch: { input: <input>, regex: <regex>, options: <options> } }`. Used for `LIKE`.

`IS NULL` and boolean-field predicates are genuinely equality comparisons, so they reuse
`AstBinaryOperatorExpression("$eq"/"$ne", operand, AstValueExpression(AstLiteral(null|true), false))` — this
is not false sharing, it is what those predicates mean. All literal operands (the `$in` options, the
`null`/`true` here) go through `AstValueExpression`.

### Changes to `AbstractMqlTranslator`

Each predicate visitor gains an `expects(EXPRESSION)` branch producing the `$expr` form, mirroring (and
reusing the helpers of) its existing filter branch. The filter branch is unchanged, so WHERE keeps the
compact, indexable `{field: {$op: value}}` form when it applies.

- **`visitJunction`** — `expects(EXPRESSION)` → `AstLogicalOperatorExpression(AstLogicalOperator.AND/OR, operands)` where
  each operand is `acceptAndYieldExpression(subPredicate)`; else the existing `AstLogicalFilter`.
- **`visitNegatedPredicate`** — `expects(EXPRESSION)` → `AstLogicalOperatorExpression(AstLogicalOperator.NOT, [acceptAndYieldExpression(inner)])`; else `$nor` filter.
- **`visitGroupedPredicate`** — `expects(EXPRESSION)` → `acceptAndYieldExpression(inner)` (passthrough); else filter passthrough.
- **`visitBooleanExpressionPredicate`** — `expects(EXPRESSION)` → `AstBinaryOperatorExpression("$eq", operand, AstValueExpression(AstLiteral(true|false), false))`; else the `{field:{$eq:true}}` filter.
- **`visitInListPredicate`** — `expects(EXPRESSION)` → `AstInExpression(acceptAndYieldExpression(test), options)` (each option via `acceptAndYieldExpression`), wrapped in `$not` when negated; else the `$in`/`$nin` filter.
- **`visitLikePredicate`** — `expects(EXPRESSION)` → `AstRegexMatchExpression(input, quoteMeta(pattern, escape), options)`, wrapped in `$not` when negated; else the regex filter. Reuses the existing `quoteMeta`/options logic.
- **`visitNullnessPredicate`** — `expects(EXPRESSION)` → `AstBinaryOperatorExpression("$eq"/"$ne", operand, AstValueExpression(AstLiteral(null), false))`; else the `{field:{$eq:null}}` filter. In EXPRESSION position the operand comes through `acceptAndYieldExpression`, so a computed operand (`x + 1 is null`) works here even though the filter form requires a field path.
- **`acceptAndYieldExpression` guard** — the blanket `Predicate` rejection is removed (predicates now translate).
  It still rejects `SqlTuple` and `ExistsPredicate` (the nodes whose visitors yield a non-EXPRESSION
  descriptor and would otherwise trip the holder assertion). Other unsupported predicates
  (`BetweenPredicate`, IN-subquery/array, filter fragments) already throw `FeatureNotSupportedException`
  in their own visitors, so they surface cleanly without a guard entry.

Dual-context helper: extend the existing pattern — a small helper per shape or an `expects(EXPRESSION)`
check inline, consistent with `visitColumnReference`/`visitRelationalPredicate`.

## Supported and Unsupported Shapes

### SELECT / expression position

| Expression | MQL | Status |
|---|---|---|
| `x > 1 and y < 2` | `{$and: [{$gt:…}, {$lt:…}]}` | ✅ |
| `x > 1 or y < 2` | `{$or: […]}` | ✅ |
| `… and not (x > 1)` (NOT as operand) | `{$not: [{$gt:…}]}` | ✅ |
| `(x > 1)` (grouped) | passthrough | ✅ |
| `… and flag` (boolean field as operand) | `{$eq: ["$flag", true]}` | ✅ |
| `x in (1, 2, 3)` | `{$in: ["$x", [1,2,3]]}` | ✅ |
| `x not in (1, 2)` | `{$not: [{$in: […]}]}` | ✅ |
| `name like 'a%'` | `{$regexMatch: {…}}` | ✅ |
| `name not like 'a%'` | `{$not: [{$regexMatch: {…}}]}` | ✅ |
| `x is null` / `x is not null` | `{$eq: ["$x", null]}` / `{$ne: …}` | ✅ |
| `x + 1 is null` (computed operand) | `{$eq: [{$add: […]}, null]}` | ✅ |
| `x between 1 and 5` | — | ❌ not supported in filters either |
| `exists (…)` | — | ❌ match-only (`$elemMatch`); tested |
| `x in (subquery)` | — | ❌ IN-subquery unsupported |

### WHERE position

The same predicates already render as `$match` filters, and that is unchanged — in WHERE these
predicates stay in filter context (a junction is an `AstLogicalFilter`, a comparison is a
`{field:{$op:value}}` filter or an `$expr` filter). HQL does not permit a predicate as a comparison
operand (`where (x in (1,2)) = (y > 0)` is a syntax error — after `(x in (1,2))` the grammar expects
`)`, `AND`, or `OR`), so a predicate reaches the value-producing expression form only in a SELECT item
or, via the Criteria API, wherever a `Predicate` is passed as an `Expression<Boolean>`. The `$expr`
expression form of an *arithmetic* operand is still reached in WHERE (e.g. `where x + 1 > 5`), as
before this work.

## Tests

Positive (in `@Nested class Positive`, seeded data), asserting full MQL + results:

| Test | Covers |
|---|---|
| `select x > 1 and y < 2` | `$and` |
| `select x > 1 or y < 2` | `$or` |
| `select (x > 1) and not (y < 5)` | `$not` (NOT as junction operand) |
| `select (x > 1) and flag` | boolean field → `$eq true` (as junction operand) |
| `select x in (1, 2, 3)` | `$in` |
| `select x not in (1, 2)` | negated `$in` → `$not` |
| `select name like 'a%'` | `$regexMatch` |
| `select name not like 'a%'` | negated LIKE → `$not` |
| `select x is null` / `is not null` | `$eq`/`$ne` null |
| `select x + 1 is null` | computed operand null-check |
| nested / composed (e.g. `select (x > 1) and (y in (1,2))`) | operators compose |

Negative (outer class / `@Nested class Unsupported`):

| Test | Covers |
|---|---|
| `select x between 1 and 5` | BETWEEN unsupported (throws `FeatureNotSupportedException`) |
| `select exists (…)` | EXISTS match-only — already in `ExistsSelectQueryIntegrationTests` |

Every non-trivial code path (each predicate's EXPRESSION branch, negated vs non-negated `IN`/`LIKE`, the
`AstLogicalOperatorExpression`/`AstInExpression`/`AstRegexMatchExpression` rendering, the shrunken guard)
must be exercised. AST-node unit tests (`assertExpressionRendering`) for each new node.

## Commits

New commits stacked on top of the current HIBERNATE-86 commits (not folded): AST nodes → translator
changes → tests, following the standard task order.

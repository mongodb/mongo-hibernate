# HIBERNATE-198: Support expressions in HQL bulk UPDATE SET clause

## Goal

Support computed expressions in the SET clause of HQL bulk `UPDATE` statements — arithmetic,
field references, and comparisons/predicates — translated to MongoDB aggregation expressions.

Example: `update Book set publishYear = publishYear + 1 where ...`.

## Background

Today `AbstractMqlTranslator.visitUpdateStatement` gates every assigned value through
`isValueExpression` (only `Literal` / `JdbcParameter` / `SqmParameterInterpretation`) and throws
`FeatureNotSupportedException` for anything else. The `u` field of the `update` command is always
rendered as a document `{ "$set": { field: value } }`.

HIBERNATE-86 already built `acceptAndYieldExpression`, which translates arithmetic, field
references, comparisons, and the scalar-predicate family into aggregation expressions, and already
throws with the correct tickets for the shapes it does not support (function calls → HIBERNATE-196,
CASE → HIBERNATE-83). This feature reuses that machinery on the UPDATE SET path.

## MongoDB Mechanism

A MongoDB `update` command's `u` field is either an **update document** or an **aggregation
pipeline**. Computed assignments use the pipeline form; the document form is kept only when every
assignment is a plain value. Update operators such as `$inc`/`$mul` are not used because they
express only `field = field op constant` — they cannot represent this feature's supported shapes
(`x = y + 1`, `x = x * y`, comparisons, the predicate family). Pipeline `$set`, reusing the
HIBERNATE-86 expression translator, covers the whole shape family with one mechanism; `$inc`/`$mul`
could at most be a narrow special case layered on top of that path, so adding them would mean
pattern-matching that shape and maintaining a second code path for no functional gain.

### All assignments are plain values → document form (unchanged)

```
update Book set title = :t, outOfStock = false where title = :old
```

```json
{ "update": "books",
  "updates": [ { "q": { "title": { "$eq": "War & Peace" } },
                 "u": { "$set": { "title": "War and Peace", "outOfStock": false } },
                 "multi": true } ] }
```

### Any assignment is computed → pipeline form

```
update Book set publishYear = publishYear + 1, outOfStock = (discount < 0.1) where id = :id
```

```json
{ "update": "books",
  "updates": [ { "q": { "_id": { "$eq": 1 } },
                 "u": [ { "$set": { "publishYear": { "$add": ["$publishYear", 1] },
                                    "outOfStock": { "$lt": ["$discount", 0.1] } } } ],
                 "multi": true } ] }
```

Within the single `$set` stage, all RHS expressions evaluate against the pre-update document, so
`set x = x + 1, y = x` uses the original `x` for both — matching SQL UPDATE semantics.

A field reference to an absent field (`$missingField`) evaluates to `missing`, which causes `$set`
to omit the target field rather than set it to null. This does not arise for Hibernate-managed
entities — every mapped column is materialized as an explicit BSON null, never absent — so a
field-reference RHS reads null (not missing) and the target is set to null, matching SQL.

String constants and string-typed parameters in pipeline (expression) position are wrapped in
`$literal` by the existing `acceptAndYieldExpression` machinery so they are not misread as field
paths; numeric/boolean literals and non-string parameters render verbatim. So in the mixed example
above, a `title = :t` assignment would render `"title": { "$literal": "<bound value>" }` while a
`outOfStock = false` assignment would render `"outOfStock": false`.

## Pipeline Structure

Not a SELECT pipeline. The change is local to the `u` payload of the `update` command: document
form when every assignment is a plain value, aggregation-pipeline form (`[ { "$set": … } ]`) when
any assignment is computed.

## Implementation Approach

### New AST nodes

- `AstComputedFieldUpdate(String name, AstExpression value)` — one pipeline `$set` field
  assignment. Renders `name: <expression>`.
- Sealed `AstUpdate` interface (the rendered value of the `u` field) with two implementations:
  - `AstDocumentUpdate(List<AstFieldUpdate> updates)` — renders `{ "$set": { … } }`.
  - `AstPipelineUpdate(List<AstComputedFieldUpdate> updates)` — renders
    `[ { "$set": { … } } ]`.

`AstFieldUpdate(String name, AstValue value)` is unchanged and remains the document-form element,
still produced by the entity-flush path (`createAstUpdateCommand`).

### Changed AST node

- `AstUpdateCommand` changes from `(String collection, AstFilter filter,
  Collection<? extends AstFieldUpdate> updates)` to
  `(String collection, AstFilter filter, AstUpdate update)`, delegating `u` rendering to the
  `AstUpdate`.

### Translator changes (`AbstractMqlTranslator.visitUpdateStatement`)

1. Determine `allValues = every assignment's assigned value satisfies isValueExpression`.
2. If `allValues`: build `AstDocumentUpdate` from `AstFieldUpdate`s via the current `VALUE` path
   (output unchanged).
3. Otherwise: for each assignment, translate the assigned value with
   `acceptAndYieldExpression(...)` into an `AstComputedFieldUpdate`, and build an
   `AstPipelineUpdate`.
4. Remove the `isValueExpression`-gate throw and `getUnsupportedUpdateValueAssignmentMessage` — the
   expression path now either translates a shape or throws with its own ticket.

`createAstUpdateCommand` (entity-flush path) is updated to wrap its `AstFieldUpdate`s in an
`AstDocumentUpdate`; its behavior is otherwise unchanged.

### JDBC execution layer (`MongoStatement`)

`MongoStatement.WriteModelConverter.createUpdateModel` currently accepts only a `BsonDocument` for
the `u` field and throws `SQLFeatureNotSupportedException("Only document type is supported as value
for field: [u]")` otherwise. The pipeline form renders `u` as a `BsonArray`, so this method gains a
branch: when `u` is a `BsonArray`, map its stages to a `List<BsonDocument>` pipeline and use the
driver's pipeline update constructors (`new UpdateManyModel<>(filter, pipeline)` /
`new UpdateOneModel<>(filter, pipeline)`); the existing `BsonDocument` path is unchanged.

The `AstUpdateCommand` signature change touches exactly three sites: the two `new AstUpdateCommand`
call sites in `AbstractMqlTranslator` (bulk update at line ~1009, entity flush at line ~1031) and
`AstUpdateCommandTests`.

The per-assignment `assertTrue(fieldReferences.size() == 1)` is retained — it guards the LHS
(assignable), which is orthogonal to this feature's RHS expressions.

## Supported and Unsupported Shapes

Single-column scalar RHS reachable from HQL `update E set field = <expr>`:

| RHS shape | Example | Status | Tracking |
| --- | --- | --- | --- |
| Literal | `set x = 5` | ✅ (document form) | — |
| Parameter | `set x = :p` | ✅ (document form) | — |
| Field reference | `set x = y` | ✅ new | — |
| Arithmetic (`+ - * / div`, unary) | `set x = x + 1` | ✅ new | — |
| Comparison as value | `set flag = (x > 1)` | ✅ new | — |
| Scalar predicate family (AND/OR/NOT/IN/LIKE/IS NULL/BETWEEN/bool field) | `set flag = (x>1 and y<2)` | ✅ new | — |
| Function call (incl. `\|\|` / `concat`) | `set x = upper(x)`, `set t = t \|\| 'x'` | ❌ throw | HIBERNATE-196 |
| CASE | `set x = case … end` | ❌ throw | HIBERNATE-83 |
| Scalar subquery | `set x = (select …)` | ❌ throw | existing |
| Duration | `set x = 1 day` | ❌ throw | existing |
| Whole-embeddable / row subquery (multi-column LHS) | `set pair = :v` | ❌ (pre-existing) | out of scope |

`||` (string concatenation) is rewritten by HQL to a `concat` function call, so it is a
FunctionExpression and falls under HIBERNATE-196.

Whole-embeddable LHS assignment (`set pair = :v`) produces a multi-column assignable and hits the
pre-existing `assertTrue(fieldReferences.size() == 1)` (an `AssertionError`, not a
`FeatureNotSupportedException`). This is unchanged by this feature and out of scope.

No new Jira tickets are required: every in-scope ❌ shape already throws with an existing ticket via
the HIBERNATE-86 expression path.

## Tests

Integration tests in `UpdatingIntegrationTests`.

### AST unit tests (`assertRendering`)

| Node | Covers |
| --- | --- |
| `AstComputedFieldUpdate` | renders `name: <expression>` |
| `AstDocumentUpdate` | renders `{ "$set": { … } }` |
| `AstPipelineUpdate` | renders `[ { "$set": { … } } ]` |
| `AstUpdateCommand` | full command with each `AstUpdate` variant |

### Positive (`@Nested class Positive`)

| Test | HQL | Covers |
| --- | --- | --- |
| arithmetic | `set publishYear = publishYear + 1` | `$add` pipeline form |
| field reference | `set publishYear = isbn13` (from converted `testPathExpressionAssignment`) | `$set` with field path |
| comparison as value | `set outOfStock = (publishYear > 2000)` (from `testPredicateExpressionAssignment`) | `$gt` pipeline form |
| predicate family | `set outOfStock = (publishYear > 2000 and isbn13 < 5)` | `$and` in expression position |
| mixed value + computed | `set title = :t, outOfStock = false, publishYear = publishYear + 1` | pipeline form applied to a computed field plus a string param (`$literal`-wrapped) and a boolean literal (verbatim) — covers `needsLiteralWrapping` both ways |
| nested embeddable computed | `set nested.a = nested.a + 1` | dotted field path in expression position → `$nested.a` (in the existing struct `@Nested` group) |
| all values (regression) | existing `testUpdateWithNonZeroMutationCount` etc. | document form unchanged |

### Negative (outer class / `@Nested class Unsupported`)

| Test | HQL | Ticket |
| --- | --- | --- |
| function call | `set title = upper(title)` (existing `testFunctionExpressionAssignment`) | HIBERNATE-196 |
| CASE | `set publishYear = case when … end` | HIBERNATE-83 |

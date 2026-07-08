# GitHub Copilot Instructions

This repository is the **MongoDB Extension for Hibernate ORM** — a Hibernate dialect and JDBC
adapter that lets applications use MongoDB as their database. It works by translating Hibernate's
internal **SQL AST** into a **MongoDB aggregation pipeline** expressed in MQL (MongoDB Query
Language).

Copilot reviewers repeatedly make **false, high-severity claims** about how a given HQL/JPQL query
is translated into MQL, because they reason from general MongoDB folklore instead of from how this
translator actually behaves. This document exists to prevent that. **Read the "HQL → MQL
translation" section below before commenting on any translation, expected-MQL string, or test
assertion.**

## How translation works

- The translator is a visitor over Hibernate's SQL AST. The entry points are
  `internal/translate/AbstractMqlTranslator` (shared logic), `SelectMqlTranslator` (SELECT →
  aggregation pipeline) and `MutationMqlTranslator` (INSERT / UPDATE / DELETE).
- The translator builds the project's **own MongoDB AST** under
  `internal/translate/mongoast/` (stages, filters, expressions). That AST is then serialized to
  BSON. Expected-MQL strings in tests are the serialization of this AST — they are **deterministic**,
  not heuristic.
- A SELECT becomes a pipeline assembled in this fixed order:
  **`$lookup`/`$unwind` (joins) → `$match` → `$sort` → `$skip`/`$limit` → `$project`**
  (see `AbstractMqlTranslator` where `stages` is assembled). Do not claim a stage appears in a
  different position unless the code shows it.
- Anything the translator cannot express throws `FeatureNotSupportedException`. Unsupported shapes
  that are planned carry a `TODO-HIBERNATE-NNN` marker with a Jira link. A query that throws is
  **not a silent miss** — do not flag it as one.

## HQL → MQL translation: authoritative operator mapping

These mappings are **fixed in code**. When reviewing, treat them as ground truth. Do **not** claim
a query is translated differently, and do **not** raise MongoDB null/missing/BSON-ordering
semantics as a correctness bug against these mappings — that behavior is intentional and matches
the operators below.

### Nullness predicates (`visitNullnessPredicate`)

| HQL                | MQL filter                        |
| ------------------ | --------------------------------- |
| `field is null`    | `{ field: { $eq: null } }`        |
| `field is not null`| `{ field: { $ne: null } }`        |

- `is not null` is translated to **`$ne: null`** — this is correct and intended. Do **not** claim it
  "should" be `$nor`/`$exists`, and do **not** report it as a critical bug that `$ne: null` or
  `$eq: null` also match missing fields. Matching missing fields is the accepted, documented
  behavior of this translator.
- `not (field is null)` goes through `visitNegatedPredicate`, which wraps the inner filter in
  **`$nor`**. `$nor: [{ field: { $eq: null } }]` and `field is not null` (`$ne: null`) are produced
  by two different code paths and are **not required to be byte-identical**. Do not file a bug that
  they differ.

### Comparison predicates (`createAstComparisonFilterOperator`)

Comparison operators map **1:1** onto MQL query operators:

| HQL / SQL operator        | MQL     |
| ------------------------- | ------- |
| `=`                       | `$eq`   |
| `<>` / `!=`               | `$ne`   |
| `<`                       | `$lt`   |
| `<=`                      | `$lte`  |
| `>`                       | `$gt`   |
| `>=`                      | `$gte`  |

- These emit the raw MongoDB comparison operators. General BSON-comparison-order behavior (e.g.
  how `$gt`/`$gte` compare against `null` or across BSON types) is **the database's semantics, not a
  bug in this translator**. Do not predict specific result sets for comparisons against `null` and
  present them as evidence of a defect.

### Boolean expression predicates (`visitBooleanExpressionPredicate`)

- A bare boolean field `flag` → `{ flag: { $eq: true } }`; negated → `{ flag: { $eq: false } }`.

### Logical connectives

- `and` → `$and`, `or` → `$or` (`visitJunction`), `not (...)` → `$nor` (`visitNegatedPredicate`).

## Review guidance — do this

- **Ground every claim in the code.** Before asserting "this HQL translates to X", point at the
  visitor method in `AbstractMqlTranslator` (or the relevant `mongoast` node) that produces X. If you
  cannot, do not assert it.
- **Trust the expected-MQL strings in tests.** They are the serialized AST. If an expected string
  looks surprising, the resolution is almost always that your mental model of MongoDB defaults
  differs from what this translator deliberately emits — not that the test is wrong.
- **Do not raise database-level MongoDB semantics as translator bugs.** How MongoDB matches
  `null`/missing or orders BSON types is out of scope for a translation review unless the code is
  clearly emitting the wrong *operator*.
- **`FeatureNotSupportedException` is a feature.** Unsupported shapes are meant to throw; a
  `TODO-HIBERNATE-NNN` marker means it is tracked. Do not flag these as incomplete/unsafe.
- **Severity discipline.** Reserve "critical"/"high" for a demonstrable defect you can tie to a
  specific line. Speculative semantic concerns are, at most, low-severity questions.

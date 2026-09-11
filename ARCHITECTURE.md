# Architecture

How the MongoDB Extension for Hibernate ORM is put together, and the invariants that new code has to
respect. `AGENTS.md` has the layer map and the build commands; this document covers the contracts that
are easy to violate without the compiler noticing.

It is the shared reference for two workflows that would otherwise each restate it:

- Implementing a new HQL construct --- `.claude/skills/add-hql-to-mql-translation/SKILL.md`
- Reviewing a pull request --- `.claude/skills/reviewing-pull-requests/SKILL.md`

This document covers *how to build it*, not *what is currently supported*. The integration test suite
is the source of truth for the latter: a construct is supported if a positive integration test
exercises it.

---

## The "SQL" string is a MongoDB command

Hibernate assumes a dialect renders statements to a SQL string, hands that string to JDBC, and lets
the driver execute it. This project keeps the shape and changes the content: the string a translator
or exporter produces is **an extended-JSON MongoDB command**, and the JDBC layer **parses** it rather
than forwarding it verbatim.

- Queries and mutations: the translators build a `mongoast` tree, which is serialized to Extended JSON
  and carried as the statement string, then executed by `MongoStatement` / `MongoPreparedStatement`.
- Schema DDL: an `Exporter` produces the same kind of string. `MongoStatement.execute(String)` parses it,
  routes a write command name to `executeUpdate(BsonDocument)`, and otherwise decodes it as an
  `AdminCommand`.

Two consequences worth internalizing. A statement string that looks like valid MQL is not proof the
feature works --- something still has to parse it on the far side. And "the server accepted it" is a
weaker claim than "the emitted command matches the mapping", because a dropped detail throws nothing.

A command can also carry a field that is not MongoDB's at all: `nonTransactional` is a JDBC-adapter
field that `MongoStatement` reads and acts on --- when `true`, it runs the command without a
`ClientSession` and without starting a transaction --- and must never forward to the driver. A new
command that forwards a document verbatim (rather than being decoded field by field, the way
`findAndModify` and the write commands are) needs to strip it first.

## Translation goes through the visitor

`AbstractMqlTranslator` walks Hibernate's SQL AST as a visitor. Values move between `visitXxx` methods
through `AstVisitorValueHolder`, keyed by an `AstVisitorValueDescriptor` that names the kind of result
the caller wants:

| Descriptor | Yields | Visibility |
|---|---|---|
| `FIELD_PATH` | a field path string | public |
| `VALUE` | `AstValue` --- a literal or parameter | public |
| `EXPRESSION` | `AstExpression` --- an aggregation expression | public |
| `FILTER` | `AstFilter` --- a query filter | public |
| `COLLECTION_NAME`, `PROJECT_STAGE_SPECIFICATIONS`, `SORT_FIELDS`, `TUPLE`, `SELECT_RESULT`, `MUTATION_RESULT` | internal plumbing | package-private |

The visibility column records the current state, not a designed boundary. The four public descriptors
are simply the ones something outside `internal.translate` has needed so far --- today, the custom
function descriptors in `internal.dialect.function`. If a new construct needs one of the others,
widening its modifier is an ordinary change, not a workaround; reaching for a hand-rolled traversal
because a descriptor is not visible is the wrong trade. Everything under `internal` stays
implementation-private to the module either way.

The protocol is: `acceptAndYield(node, DESCRIPTOR)` to get a child's translation; `yield(DESCRIPTOR,
value)` to produce your own; `expects(DESCRIPTOR)` to ask what the caller wants when a node can
legitimately translate more than one way (a comparison is a `FILTER` in a WHERE clause and an
`EXPRESSION` in a projection).

**Do not add a parallel recursive descent.** Walking the AST yourself --- your own `switch` over node
types, your own child-recursion, your own argument loop --- to avoid modifying existing `visitXxx`
methods produces code that cannot see the translator's state and silently diverges from the real
visitor as it evolves. It also bypasses `expects(...)`, and with it the filter-form decision below.

**Carrying state in translator fields is the sanctioned alternative.** Adding a field to
`AbstractMqlTranslator` is normal practice here, not a smell; `elemMatchInnerAlias`,
`letVariableCounter`, `projectionKeyMap`, and `joinedTableQualifiers` all exist for exactly this. When
a construct needs context that the visited node cannot carry, add a field and modify the visitor.

## Parameter binders are collected while rendering

A JDBC parameter carries no identity into the emitted command: it renders as BSON `undefined`, and
`MongoPreparedStatement` recovers the parameter positions by walking the parsed command depth-first,
in document-entry then array-index order. Hibernate then binds the i-th `JdbcParameterBinder` the
translator produced into the i-th of those positions, so the two orders have to agree.

They agree by construction. `visitParameter` yields an `AstParameterMarker` holding its own binder, and
`AstNode.render` takes a `Consumer<JdbcParameterBinder>`: the marker appends its binder in the same
statement that writes the `undefined`. Every binder in the list corresponds to a marker that was
actually rendered, in the order it was rendered.

The translator therefore imposes no ordering rule on the code that builds the AST. Reordering
translated nodes is fine. Dropping one drops its binder with it. Reusing a single translated node in
two positions renders two markers and collects its binder twice, which binds the same value at both
positions, so a construct whose operand appears more than once may translate it once and share it
rather than visiting it per occurrence.

## Filter form: two languages in `$match`, one in `$project`

Two MongoDB languages are in play, and only one of the two names below is MongoDB's own:

- **Aggregation expressions** --- the [expression operator](https://www.mongodb.com/docs/manual/reference/operator/aggregation/)
  language (`$toUpper`, `$strLenCP`, `$eq`-as-expression). This is MongoDB's term.
- **Find syntax** --- the [query operator](https://www.mongodb.com/docs/manual/reference/operator/query/)
  language of `db.collection.find()` filters and `$match` predicates. "Find syntax" is our shorthand;
  MongoDB calls these query operators, so search the manual for that.
- [`$expr`](https://www.mongodb.com/docs/manual/reference/operator/query/expr/) is the bridge that
  admits an aggregation expression into the query language.

`$project` accepts **only** aggregation expressions; there is no find-syntax alternative there. So any
construct that can appear in a projection needs an aggregation-expression translation, full stop.

`$match` accepts two languages:

| Language | Example | Applies to |
|---|---|---|
| find syntax (query operators) | `{"n": {"$eq": 7}}` | the narrow set of shapes MongoDB's query language expresses directly |
| `$expr` wrapping an aggregation expression | `{"$expr": {"$eq": [{"$strLenCP": "$s"}, 7]}}` | everything else --- which is most constructs |

**Find syntax is the restricted language, not the general one.** It reaches a fixed set of query
operators, and the repo has a dedicated `AstFilter` node per operator it uses
(`AstComparisonFilterOperation`, `AstListComparisonFilterOperation`, `AstAllFilterOperation`,
`AstElemMatchFilterOperation`, `AstTypeFilterOperation`, `AstRegularExpressionFilterOperation`). Prefer
it where it applies, because it is more readily indexable. But as soon as an operand is computed rather
than a plain field-versus-value comparison, find syntax cannot express it and `$expr` is the correct
answer, not a fallback to apologize for.

For a comparison specifically, the choice is made in `toFilter`:

| Operand shape | Rendering | Path |
|---|---|---|
| field vs. value --- `t.n = 7` | `{"n": {"$eq": 7}}` | `isComparingFieldWithValue` → `toFieldValueFilter` |
| computed operand --- `length(t.s) = 7` | `{"$expr": {"$eq": [{"$strLenCP": "$s"}, 7]}}` | falls through to `AstExprFilter` |

So the design question for a new construct is: what is its aggregation expression --- needed for
`$project`, and for `$expr` inside `$match` --- and separately, is there a find-syntax form worth
taking for the field-versus-value case? Emitting `$expr` where the compact form was available costs
indexability; emitting find syntax where the semantics need an expression is a correctness bug.

## Clause-position parity

A construct should work in both `SELECT` and `WHERE` so far as is feasible. These are different
branches --- one emits into `$project`, the other into `$match` --- so support in one is not evidence
of support in the other, and a projection-only implementation is incomplete rather than done.

Where a position is genuinely out of scope, it needs a negative test asserting the throw and a Jira
ticket, not silence. Reachable-but-untested is the recurring failure mode in this codebase.

## Aggregation pipeline stage order

`SELECT` translation assembles stages in a fixed order:

```
($lookup + $unwind)*  →  $match  →  $sort  →  $skip / $limit  →  $project
```

One `$lookup` + `$unwind` pair per join. Nested joins are emitted depth-first: a join hanging off an
already-joined entity produces its pair immediately after its parent's, ahead of the parent's next
sibling.

New stages have to be placed deliberately within that sequence, not appended.

## Failure contract

The exception a construct throws is part of its contract, and the two kinds mean opposite things:

| Thrown | Meaning |
|---|---|
| `FeatureNotSupportedException` with `TODO-HIBERNATE-NNN` and the Jira URL | A gap we intend to close. The `TODO-` prefix is the "not yet implemented" signal, and the ticket must actually describe the shape that throws. |
| `FeatureNotSupportedException` with a bare message | A deliberate refusal, with nothing tracked and nothing promised. Some of these are permanent --- MQL cannot express the construct at all --- and those need no ticket. The message should carry enough reason for a reader to tell which. |
| `AssertionError` from `MongoAssertions` (`assertTrue`, `assertNotNull`, `fail`) | An **internal invariant was violated** --- always a bug, never a legitimate response to user input. Most often a descriptor mismatch in `AstVisitorValueHolder.yield`. |

A construct that reaches a bare `AssertionError` for valid HQL is a defect even if the HQL is
something we do not intend to support: the correct behaviour is a `FeatureNotSupportedException`
naming a ticket.

## Schema DDL

Schema export reuses the pattern above: the dialect renders a command, and the JDBC layer parses it.

```
Dialect exporter --- getIndexExporter() / getUniqueKeyExporter()
        ↓  a createIndexes / dropIndexes command, carried as the statement string
MongoStatement.execute(String)
        ↓  decoded into a typed command
MongoDB Java driver
```

Hibernate requests an `Exporter` per kind of `Exportable`, and each one's `getSqlCreateStrings` /
`getSqlDropStrings` returns statement strings. `getTableExporter()` has nothing to render, because
MongoDB creates collections implicitly and has no `CREATE TABLE` analogue.

That absence determines which mappings can be honoured. Hibernate models some constraints as their own
exportable --- `@Index` and `@Table(uniqueConstraints = ...)` --- and leaves others to be inlined into
table DDL by the table exporter, `@Column(unique = true)` among them. Only the first group reaches an
exporter here, so anything in the second group has to be handled off the exporter path to have any
effect at all. A Hibernate sequence is the other exportable this layer honours: `MongoDialect` returns
`MongoSequenceSupport` from `getSequenceSupport()`, and `StandardSequenceExporter` calls it to render
the create/drop commands for the counter collection backing `@GeneratedValue`.

Export runs at `SessionFactory` build time, driven by
`jakarta.persistence.schema-generation.database.action` --- so it is exercised by building a factory,
not by running HQL.

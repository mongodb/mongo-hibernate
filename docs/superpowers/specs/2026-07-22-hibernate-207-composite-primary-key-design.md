# HIBERNATE-207: Composite primary key — design

## Summary

Support a composite primary key declared with `@EmbeddedId` on a plain `@Embeddable`, stored as the
document's `_id` sub-document. In scope: entity CRUD by id, and HQL filtering / projection / ordering / bulk
mutation over the id and its components. The supported id embeddable is flat — every component is a basic value. 
Out of scope (each rejected with a clear error, tracked separately): `@IdClass`; `@Struct` on the id embeddable; a 
non-scalar id component (a nested embeddable or collection); derived identity (`@MapsId` — an association *inside* the 
id); and associations whose foreign key is a composite key.

## MongoDB mechanism

A MongoDB document has exactly one primary-key field, `_id`, and no native multi-column primary key. A composite
key is therefore modelled as a single `_id` whose value is a sub-document holding the components (shown with
`publisherId = 1, bookNo = 2`):

```
{ "_id": { "bookNo": 2, "publisherId": 1 }, "title": "…" }
```

Uniqueness is enforced by the mandatory `_id` index, which compares the whole `_id` value. BSON sub-document equality is
field-order-sensitive (`{ a, b }` ≠ `{ b, a }`), so a given logical key must always serialize its components in the
same field order — otherwise the index would treat two encodings of one key as distinct.

## Dialect design

**Column naming (boot).** The dialect names the id's leaf columns `_id.<component>` (e.g.
`_id.publisherId`, `_id.bookNo`) and assembles them into the `_id` sub-document in a canonical order — components
sorted by name. Canonical order is required by the field-order sensitivity above; the dialect deliberately does
*not* inherit Hibernate's own column order, which is incidental and inconsistent (a plain `@Embeddable` **class**
id sorts alphabetically via `Component.sortProperties`, a **record** id keeps declaration order, neither
contractual). A single-component `@EmbeddedId` is unchanged — it keeps flattening to a scalar `_id`; only a
multi-component id becomes a sub-document.

**What the AST gives the translator.** Because the id's columns are named `_id.<component>`, Hibernate's SQL AST
already expresses every id operation per component — the translator is handed no whole-`_id` value except on
insert:

- A whole-id restriction — `where b.id = :id` / `<> :id` / `in :ids`, and the literal forms `= (2, 1)` /
  `in ((2, 1), (4, 3))` — arrives **already decomposed**, as a `SqlTuple` of per-component column/value elements
  that HIBERNATE-210's row-value translation turns into a per-component `$and` / `$nor` / `$or`. A bound
  `setParameter("id", new BookId(…))` and a literal tuple decompose identically, so both share one path. (The
  literal tuple's elements bind positionally in canonical column order, `(bookNo, publisherId)`.)
- A component predicate (`b.id.bookNo = 1`), `order by b.id`, and `select b.id` / `select b.id.<component>` arrive
  as ordinary `_id.<component>` path references, handled by the existing column-reference / nested-path machinery
  (leaves projected as `_id#<component>`, reassembled by Hibernate's embeddable assembler).
- On **insert** the AST supplies the id as leaf columns: one value binding per `_id.<component>` (managed persist),
  or — for HQL bulk `INSERT … values` — a single tuple-valued expression spanning the id's columns while the
  target list is flattened to leaves (so the id target and value align by column, not by expression count). The id
  must be named as a whole target (`id`); HQL rejects naming id components as insert targets, and a literal tuple
  there is rejected by HQL (typed `Object[]`, not assignable to the embeddable).
- On managed / bulk UPDATE and DELETE the key arrives as per-component key bindings.

**So the translator's only composite-specific work is** (a) on insert, gather the flat `_id.<component>` leaves
into the one `_id` sub-document in canonical order, and (b) on UPDATE / DELETE, `AND` the per-component key
bindings. Filtering, ordering, and projection over the id need no composite-specific code — they go through the
existing `_id.<component>` path handling and HIBERNATE-210.

Representative MQL for the in-scope operations (`publisherId = 1, bookNo = 2`; components in canonical name order):

```
// persist
insert: documents: [ { "_id": { "bookNo": 2, "publisherId": 1 }, "title": "…" } ]

// find by id  /  HQL `where b.id = :id`
$match: { $and: [ { "_id.bookNo": {$eq: 2} }, { "_id.publisherId": {$eq: 1} } ] }

// HQL `where b.id in (k1, k2)`
$match: { $or: [ { $and: [ …k1… ] }, { $and: [ …k2… ] } ] }

// HQL `where b.id.publisherId = 1`
$match: { "_id.publisherId": {$eq: 1} }

// HQL `order by b.id`
$sort: { "_id.bookNo": 1, "_id.publisherId": 1 }

// managed update / bulk update by id
update: [ { q: { $and: [ … ] }, u: { $set: { … } }, multi: true } ]

// managed delete / bulk delete by id
delete: [ { q: { $and: [ … ] }, limit: 0 } ]

// select b.id  (leaves projected, reconstructed by Hibernate's embeddable assembler)
$project: { "_id#bookNo": "$_id.bookNo", "_id#publisherId": "$_id.publisherId" }
```

## Pipeline structure

Composite ids add no new pipeline stages and no change to stage order; the existing
`$match → $sort → $skip/$limit → $project` shape is untouched. Only the content of existing stages and the
mutation commands changes: the `$match` filter (per-component `$and` / `$or`), the `$sort` keys (component paths),
the `$project` (component leaves), the `insert` document (`_id` assembled from components), and the `update` /
`delete` restriction (per-component `$and`).

## Implementation approach

No new AST node classes are required: `_id` assembly reuses `AstDocument` / `AstElement` (a document is already an
`AstValue`), and per-component filters reuse `AstLogicalFilter` (`$and` / `$or`).

**Boot — `internal/boot/MongoAdditionalMappingContributor`:**

- `setIdentifierColumnName`: for a multi-component embeddable identifier, name each component column
  `_id.<component>` instead of throwing the "primary key spanning multiple columns" error. Single-component ids
  keep flattening to scalar `_id`. The `_id.` names are introduced after `checkColumnNames`, so the `.` is not
  wrongly rejected as an unsupported field-name character.
- `forbidStructIdentifier`: unchanged — continues to reject `@Struct` on the id embeddable, redirecting the user to
  a plain `@Embeddable`.
- New guards, each throwing `FeatureNotSupportedException` naming the shape's support ticket, replacing today's
  misleading error / NPE. Every id component must be a basic value; the guard distinguishes a non-scalar component
  (a nested embeddable or collection — HIBERNATE-236) from an association component (derived identity / `@MapsId`
  — HIBERNATE-237). A separate guard rejects `@IdClass` (HIBERNATE-235; whose non-aggregated / virtual id trips
  different boot assumptions), resolving HIBERNATE-230 Case 1 (today an NPE naming an internal Hibernate type).

**Translator — `internal/translate/AbstractMqlTranslator`:**

- `visitStandardTableInsert`: gather the `_id.<component>` value bindings into a nested `_id` sub-document,
  ordering the sub-document's components by name (the dialect's canonical order — see the field-order note above),
  not by Hibernate's binding order.
- `visitInsertStatement` (HQL bulk `INSERT … values`): gather the id's target columns into an `_id` sub-document
  (same canonical component ordering as above). Unlike a `where` clause, the composite-id value here is a single
  expression spanning the id's columns while the target list is flattened to the leaf columns, so the id target and
  its value are aligned by column (not by expression count) before gathering. The id must be named as a whole
  target (`id`) — HQL itself rejects naming id components as insert targets. (Insert with a source `select` is a
  separate, pre-existing dialect limitation, unaffected by this ticket.)
- `createKeyFilter` (managed / bulk UPDATE and DELETE): `AND` the per-component key bindings (drop the
  single-column guard).

## Supported and unsupported shapes

Supported (this ticket):

| Shape | |
|---|---|
| Multi-component `@EmbeddedId`, plain `@Embeddable`, basic-valued components → `_id` sub-document | ✅ |
| `persist` / `find` / `update` / `delete` / `getReference` by id | ✅ |
| HQL `where b.id = :id`, `where b.id <> :id`, `where b.id in :ids`, `where b.id.<component> = ?` | ✅ |
| HQL `select b.id`, `select b.id.<component>`, `order by b.id` | ✅ |
| HQL bulk `update` / `delete` by id; bulk `INSERT … values` with the id as a whole target | ✅ |

Unsupported — each rejected with a clear `FeatureNotSupportedException`:

| Shape | Disposition |
|---|---|
| `@IdClass` composite key | HIBERNATE-235; 207 adds the boot guard, resolving HIBERNATE-230 Case 1 (today an NPE) |
| Non-scalar id component (nested embeddable or collection) | HIBERNATE-236 |
| Derived identity / `@MapsId` (association inside the id) | HIBERNATE-237 |
| Ordering whole-id comparison (`b.id > :x`, `<`, `>=`, `<=` — lexicographic) | Existing HIBERNATE-211 — already throws `TODO-HIBERNATE-211` via HIBERNATE-210's row-value path; no 207 work |
| `@Struct` on the id embeddable | Permanent redirect via existing `forbidStructIdentifier` ("use `@Embeddable`") — no ticket |
| Association whose foreign key is a composite key | Existing HIBERNATE-164 |

## Tests

A dedicated `CompositePrimaryKeyIntegrationTests`, organized into four `@Nested` classes by concern — `Insert`,
`Query`, `Mutation`, `Unsupported` (each implements `MongoServiceRegistryProducer`). `Query` and `Mutation` seed
via `persist` in `@BeforeEach`; `Insert` starts from an empty collection and asserts the `insert` command MQL
directly, so a persist regression fails a named test rather than only a shared precondition. Every id is
asymmetric with disjoint component ranges (e.g. `publisherId` ≥ 10, `bookNo` ≤ 9), so a component-order swap
anywhere in the translator would change a result and fail a test. Each positive asserts the full MQL, the full
result set, and the affected collections. The `@Struct`-on-id negative stays in
`StructAggregateEmbeddableIntegrationTests.Unsupported.testStructPrimaryKey`.

Positive (entity keyed on `(publisherId, bookNo)`):

| Test | Covers |
|---|---|
| persist | `insert` `_id` gather |
| find by id | per-component `$and` match + component projection + id reconstruction |
| managed update | load + `update` (per-component `q`, `$set`) |
| managed delete | load + `delete` (per-component `q`) |
| `where b.id = :id` | tuple `=` → `$and` (`toFilter`) |
| `where b.id <> :id` | tuple `<>` → `$nor` of `$and` (`toFilter`) |
| `where b.id = (2, 1)` (literal tuple, `(bookNo, publisherId)`) | literal row-value → same per-component path as the parameter form |
| `where b.id in :ids` (≥ 2) | tuple `in` → `$or`-of-`$and` (`visitInListPredicate`, multi-row) |
| `where b.id.publisherId = ?` | component-path predicate |
| `select b.id` | multi-component projection + reconstruction |
| `select b.id.bookNo` | single-component projection |
| `order by b.id` | `$sort` on component paths |
| bulk `update … where b.id = :id` | `createKeyFilter` per-component |
| bulk `delete … where b.id = :id` | `createKeyFilter` per-component |
| bulk `INSERT … values (:id, …)` | `visitInsertStatement` id-target gather + column alignment |
| duplicate-id insert → duplicate-key error | native `_id` uniqueness (`E11000` on `_id_`) |
| `_id` component order for a class id and a record id | dialect-pinned canonical order, independent of Hibernate's alphabetical-vs-declaration ordering |
| managed update on a `@Version` composite-id entity | key filter `$and`[components, version]; `$set` increments version |

Negative (`FeatureNotSupportedException`; real ticket number in each message):

| Test | |
|---|---|
| `@IdClass` entity | HIBERNATE-235 (guard resolves HIBERNATE-230 Case 1) |
| non-scalar id component (nested-embeddable component) | HIBERNATE-236 |
| `@MapsId` / association-in-id | HIBERNATE-237 |
| ordering whole-id comparison (`b.id > :x`) | confirms the composite-id path reaches HIBERNATE-210's `TODO-HIBERNATE-211` throw |
| `@Struct` on id embeddable | existing — covered by `StructAggregateEmbeddableIntegrationTests.Unsupported` |

These cover every non-trivial code path: both `in`-list branches (single- and multi-row), the comparison branch,
the insert gather, the key-filter loop, and the boot component-renaming loop.

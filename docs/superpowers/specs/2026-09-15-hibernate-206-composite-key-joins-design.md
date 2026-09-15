# HIBERNATE-206: Composite key joins — design

## Summary

Support associations and joins involving an entity whose primary key is a composite — an `@EmbeddedId` on a
plain `@Embeddable`, stored as the document's `_id` sub-document (the model from HIBERNATE-207). Two
capabilities: a `@ManyToOne` whose target has a composite key, with the foreign key stored as a sub-document;
and HQL joins over composite keys, both mapped association joins and explicit entity joins with `ON`.

Out of scope, tracked separately and still rejected at boot: `@IdClass` (HIBERNATE-235), a non-scalar id
component (HIBERNATE-236), derived identity / `@MapsId` (HIBERNATE-237). Nothing in this design touches those
rejections: they guard associations *inside* an id, while this ticket is about associations *to* a
composite-key entity.

## MongoDB mechanism

A composite foreign key is stored as a sub-document mirroring the target's `_id` shape, named after the
association property. A review joined to the book with `publisherId = 10, bookNo = 2` (components in the
canonical name order HIBERNATE-207 established) is stored as:

```
{ "_id": 1, "comment": "…", "book": { "bookNo": 2, "publisherId": 10 } }
```

Every join over composite keys is a `$lookup` in the pipeline form — `let` variables for the outer columns,
`$expr` with an `$and` of per-component equalities. For the mapped association `JOIN r.book` from `Review`:

```
{ $lookup: {
    from: "books",
    let: { v0: "$book.bookNo", v1: "$book.publisherId" },
    pipeline: [ { $match: { $expr: { $and: [
        { $eq: [ "$$v0", "$_id.bookNo" ] },
        { $eq: [ "$$v1", "$_id.publisherId" ] } ] } } } ],
    as: "#b1_0" } }
{ $unwind: "$#b1_0" }
```

For an explicit entity join `FROM Book b JOIN Review r ON b.id = r.id`, the outer columns are the root's
`_id.<component>` paths:

```
{ $lookup: {
    from: "reviews",
    let: { v0: "$_id.bookNo", v1: "$_id.publisherId" },
    pipeline: [ { $match: { $expr: { $and: [
        { $eq: [ "$$v0", "$_id.bookNo" ] },
        { $eq: [ "$$v1", "$_id.publisherId" ] } ] } } } ],
    as: "#r1_0" } }
```

Both shapes are the same machinery HIBERNATE-164 built for compound scalar ON conditions; only the column
paths are composite-specific. The simple `localField`/`foreignField` form could in principle express a
whole-sub-document equality (`localField: "book"`, `foreignField: "_id"`), but it is deliberately not taken:
it silently depends on BSON field-order-sensitive equality across two independently-named sub-documents, and
the pipeline form needs no new detection logic.

Variable names and the join alias follow the existing conventions (`v<n>_c…`, `#<alias>`); the examples above
shorten them for readability.

## Pipeline structure

Unchanged. One `$lookup` + `$unwind` pair per join, in the established order
`($lookup + $unwind)* → $match → $sort → $skip/$limit → $project`, nested joins depth-first. A composite-key
join adds no new stage kinds.

## Implementation approach

**Boot: composite foreign-key naming.** For a `ToOne` association whose target id is a multi-component
`@EmbeddedId`, Hibernate derives flat default foreign key column names (`book_publisherId`, `book_bookNo`). The
contributor renames the foreign key's leaf columns to `<association>.<component>` (e.g. `book.publisherId`,
`book.bookNo`), positionally aligned with the target id's components, so the foreign key is stored as a sub-document
mirroring the target's `_id` shape. The step must run after `checkColumnNames` (the renamed dot-path names
would trip the '.' ban, the same reason `setIdentifierColumnName` runs after it). An inverse-side
`ToOne` owns no foreign key columns and is skipped; a `@JoinColumn` override on a composite-key association is
rejected at boot with a bare message — the sub-document layout requires the `<association>.<component>` names,
so the rename would silently discard the override's names — and a count mismatch between foreign key columns and
target id components is asserted as an internal invariant, being unreachable from user input.

**Translator: recurse through virtual nested table groups.** When an ON predicate references the joined
entity's composite id (`ON b.id = r.id`), Hibernate gives the embeddable path a `StandardVirtualTableGroup`
added as a *nested* table group join on the joined entity's group. It contributes no stage and carries no columns of
its own; it exists so the embeddable's column references resolve. Today `buildJoinStages` throws
`TODO-HIBERNATE-168` for any nested group. The change follows Hibernate's own SQL translator: process nested
table group joins recursively, skipping virtual ones, and throw `TODO-HIBERNATE-168` only for a real nested
group (implicit association navigation inside an ON clause, HIBERNATE-168's actual subject).

**Translator: no composite-specific predicate code.** The ON comparison arrives as a `SqlTuple` on both sides
(verified in the Hibernate 7.4.7 sources: `AbstractCompositeIdentifierMapping.toSqlExpression`); the existing
row-value decomposition (HIBERNATE-210 / HIBERNATE-207) splits it into per-component equalities, and inside
the `$lookup` sub-pipeline the general visitors bind outer columns to `let` variables
(`visitColumnReference`). For a mapped association, Hibernate's `EmbeddedForeignKeyDescriptor` generates the
per-component `Junction` the pipeline form already consumes. A whole-composite equality in the WHERE clause
decomposes the same way and needs no new code.

**Translator: insert-time sub-document assembly generalized.** The 207-era assembly gathered only
`_id.<component>` siblings back into the `_id` sub-document; it now gathers every extension-generated
dot-path column under its first segment, so the foreign key bindings land in their `<association>` sub-document.
Components are sorted by name (the `_id` unique index compares the sub-document field-order-sensitively);
top-level field order follows binding order, which the server normalizes for `_id`. Updates need no assembly:
`$set` with dot notation writes the nested fields directly.

## Supported and unsupported shapes

| Shape | Status |
|---|---|
| Explicit entity join, whole-id ON (`JOIN Review r ON b.id = r.id`), INNER | ✅ |
| Explicit entity join, whole-id ON plus conjunct (`… AND b.title = r.comment`) | ✅ |
| Explicit entity join, LEFT (`LEFT JOIN Review r ON b.id = r.id`) | ✅ |
| Association-path join to composite-key target (`JOIN r.book b`), `@ManyToOne` with composite foreign key | ✅ (after boot naming) |
| Inverse-side collection join (`JOIN b.reviews r`, `@OneToMany` mappedBy) | ✅ — same pipeline form, foreign key paths on the joined side (no test exercises this shape) |
| `JOIN FETCH` of a composite-key association | ✅ — projection goes through the existing embeddable assembler |
| WHERE / ORDER BY on joined composite id components (`WHERE r.book.id.publisherId = 10`) | ✅ — the foreign key sub-document's dot-path in the outer `$match` |
| Whole-id ordering comparison (`ON b.id > r.id`, `b.id < r.id`) | ❌ — HIBERNATE-211, existing throw |
| Explicit entity join with `ON` navigating a further association (`ON r.book.publisher.name = …`) | ❌ — HIBERNATE-168, unchanged |
| `@ManyToMany` with a composite key on either side | ❌ — HIBERNATE-264; the join-table collection would need the foreign key sub-document layout on both sides |
| `@IdClass` composite key | ❌ — HIBERNATE-235 |
| Association inside an id / `@MapsId` | ❌ — HIBERNATE-237 |
| Non-scalar id component | ❌ — HIBERNATE-236 |

An array of foreign keys (a parent document holding a BSON array of composite keys) is not a mapping this
dialect produces: entity associations are relational (foreign key on the child, join tables as their own collection),
and BSON arrays are reserved for element collections. Unchanged by this ticket.

## Tests

The tests live in `src/integrationTest/java/com/mongodb/hibernate/query/select/CompositeKeyJoinIntegrationTests.java`.
Its shared `@DomainModel` seeds `Book` and `Review`, both with `@EmbeddedId` composite keys of the same shape
(`BookId`/`ReviewId(publisherId, bookNo)`), plus `Driver` and `License` for a bidirectional `@OneToOne`
association. `Review.book` is a `@ManyToOne` whose foreign key is stored as a sub-document named after the
association.

| Test | Covers |
|---|---|
| `testWholeIdEquijoin` — `ON b.id = r.id`, full MQL + results (RED today: `TODO-HIBERNATE-168`) | tuple decomposition through `$lookup`, virtual nested-group recursion |
| `testWholeIdEquijoinPlusConjunct` — `ON b.id = r.id AND b.title = r.comment` | mixed composite + scalar conjuncts, multiple `let` variables |
| `testLeftOuterWholeIdEquijoin` — LEFT variant, non-matching seeds retained | preserve path of the pipeline form |
| `testWhereOnJoinedIdComponent` — `WHERE r.book.id.publisherId = 10` | foreign key sub-document dot-path in the outer `$match` |
| `testManyToOneCompositeForeignKeyInsert` — persist a `Review` with `book` set | foreign key sub-document layout in the insert command |
| `testAssociationJoin` — `JOIN r.book b` | `EmbeddedForeignKeyDescriptor` junction through the pipeline form |
| `testJoinFetchCompositeAssociation` — `JOIN FETCH r.book` | full projection of the composite-keyed target |
| `testInverseSideHasNoForeignKeyColumns` — bidirectional `@OneToOne` (`Driver`/`License`) | inverse-side skip; owning-side foreign key sub-document on insert |
| `testWholeIdOrderingComparisonOnJoin` — `ON b.id > r.id` | `TODO-HIBERNATE-211` through the join path |
| `testJoinColumnOnCompositeAssociationRejected` — `@JoinColumns` override on a composite-key association fails boot | bare-message refusal |

Each positive test asserts the full MQL pipeline, the full result set, and the affected collections via
`assertSelectionQuery`; each refusal test asserts the documented throw.

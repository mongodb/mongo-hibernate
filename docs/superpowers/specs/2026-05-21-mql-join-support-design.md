# MQL Join Support (HIBERNATE-145) — Design Spec

## Goal

Add JOIN support to the MQL (aggregation-pipeline) translator, covering INNER JOIN, LEFT OUTER JOIN, and JOIN FETCH. Unsupported join shapes throw `FeatureNotSupportedException` with a Jira ticket reference rather than silently producing wrong queries.

## Background

The MQL translator (`AbstractMqlTranslator`) converts Hibernate's SQL AST into a MongoDB aggregation pipeline. It currently rejects all joins with `TODO-HIBERNATE-65`. The MQLv2 translator on the `mqlv2` branch already supports joins via its own native `join` operator; this design covers the equivalent work for MQL using standard MongoDB aggregation pipeline stages.

## MongoDB Mechanism

Joins in the MongoDB aggregation pipeline use two stages:

**`$lookup`** — fetches matching documents from another collection into an array field on each outer document:
```json
{ "$lookup": { "from": "orders", "localField": "_id", "foreignField": "customerId", "as": "o1_0" } }
```

**`$unwind`** — flattens the array, producing one output document per matched inner document:
- INNER JOIN: `{ "$unwind": "$o1_0" }` — drops outer documents with no matches (default `preserveNullAndEmptyArrays: false`)
- LEFT OUTER JOIN: `{ "$unwind": { "path": "$o1_0", "preserveNullAndEmptyArrays": true } }` — keeps outer documents with no matches, setting the joined path to `null`

After `$unwind`, joined-table columns are accessible at dotted paths (e.g., `o1_0.total`). `$match` and `$sort` reference them via these dotted paths. `$project` uses flat field-path expressions with `#` as the separator (e.g., `"o1_0#total": "$o1_0.total"`) — see the Result Reading section.

This initial implementation uses the simple `$lookup` form (`localField`/`foreignField`), which supports a single field-pair equijoin. Complex ON conditions require the `$lookup` pipeline form with `$expr` and are tracked as follow-up tickets.

## Pipeline Structure

```
[$lookup + $unwind per joined table, in join order]
  → [$match]  → [$sort]  → [$skip/$limit]  → [$project]
```

Joins are emitted first so that `$match`, `$sort`, and `$project` can all freely reference joined-table columns. Pushing root-table filters before `$lookup` for performance is out of scope.

## Concrete Examples

### Single INNER JOIN

HQL: `SELECT c.id, o.total FROM Customer c JOIN Order o ON c.id = o.customerId WHERE o.total > 100`

```json
{ "aggregate": "customers", "pipeline": [
  { "$lookup": { "from": "orders", "localField": "_id", "foreignField": "customerId", "as": "o1_0" } },
  { "$unwind": "$o1_0" },
  { "$match": { "o1_0.total": { "$gt": 100 } } },
  { "$project": { "_id": true, "o1_0#total": "$o1_0.total" } }
]}
```

### LEFT OUTER JOIN

HQL: `SELECT c.id, o.total FROM Customer c LEFT JOIN Order o ON c.id = o.customerId`

```json
{ "aggregate": "customers", "pipeline": [
  { "$lookup": { "from": "orders", "localField": "_id", "foreignField": "customerId", "as": "o1_0" } },
  { "$unwind": { "path": "$o1_0", "preserveNullAndEmptyArrays": true } },
  { "$project": { "_id": true, "o1_0#total": "$o1_0.total" } }
]}
```

Customers with no orders appear with `o1_0#total` projected as `null`.

### Chained (Three-Way) INNER JOIN

HQL: `SELECT c.id, o.id, li.quantity FROM Customer c JOIN Order o ON c.id = o.customerId JOIN LineItem li ON o.id = li.orderId`

```json
{ "aggregate": "customers", "pipeline": [
  { "$lookup": { "from": "orders",     "localField": "_id",      "foreignField": "customerId", "as": "o1_0"  } },
  { "$unwind": "$o1_0" },
  { "$lookup": { "from": "line_items", "localField": "o1_0._id", "foreignField": "orderId",    "as": "li1_0" } },
  { "$unwind": "$li1_0" },
  { "$project": { "_id": true, "o1_0#_id": "$o1_0._id", "li1_0#quantity": "$li1_0.quantity" } }
]}
```

The second `$lookup` uses `localField: "o1_0._id"` (a dotted path) because `o`'s columns are embedded at `o1_0.*` after the first `$unwind`. MongoDB's `$lookup` supports dotted `localField` paths.

**Implementation note:** This example assumes Hibernate places the `LineItem` join on `Order`'s `TableGroup` (not the root `Customer` `TableGroup`), which is what the MQLv2 recursive traversal implies. Verify this with SQL AST logging during implementation; if all joins are flat under the root, the recursion still works correctly (just never finds sub-joins during recursive calls).

## Implementation Approach

Approach A — surgical additions to `AbstractMqlTranslator` plus two new AST node classes. No new translator classes; the visitor pattern is retained for expression and predicate handling. Join traversal uses an imperative recursive helper (`buildJoinStages`), which is a natural fit for the structural (non-expression) part of the AST.

### New AST Nodes

Three new classes in `mongoast/command/aggregate/`:

**`AstLookupStage`**
```java
record AstLookupStage(String from, String localField, String foreignField, String as)
        implements AstStage {
    // renders: { "$lookup": { "from": ..., "localField": ..., "foreignField": ..., "as": ... } }
}
```

**`AstUnwindStage`**
```java
record AstUnwindStage(String path, boolean preserveNullAndEmptyArrays) implements AstStage {
    // preserveNullAndEmptyArrays=false: { "$unwind": "$<path>" }
    // preserveNullAndEmptyArrays=true:  { "$unwind": { "path": "$<path>", "preserveNullAndEmptyArrays": true } }
}
```

**`AstProjectStageFieldPathSpecification`**
```java
record AstProjectStageFieldPathSpecification(String field, String fieldPath)
        implements AstProjectStageSpecification {
    // renders: { "<field>": "$<fieldPath>" }
    // e.g. "o1_0#total": "$o1_0.total"
}
```

### Changes to `AbstractMqlTranslator`

**New field:**
```java
private final Set<String> joinedTableQualifiers = new HashSet<>();
```

Maps joined-table identification variables (e.g., `"o1_0"`) to themselves — the identification variable is both the key and the dotted-path prefix used in all downstream stage references.

**`visitFromClause`** — remove the `hasRealJoins()` guard. The single-root and entity-persister checks remain. The mutation validation path (`checkMutationStatementSupportability`) gets an explicit `hasRealJoins()` check added, since joins in mutations are not supported.

**`visitQuerySpec`** — insert join stages before `$match`:
```java
var root = querySpec.getFromClause().getRoots().get(0);
stages.addAll(buildJoinStages(root));  // emitted first
createMatchStage(querySpec).ifPresent(stages::add);
// sort, skip/limit, project unchanged
```

**`buildJoinStages(TableGroup)`** — recursive helper:
```java
private List<AstStage> buildJoinStages(TableGroup tableGroup) {
    var stages = new ArrayList<AstStage>();
    for (var tgj : tableGroup.getTableGroupJoins()) {
        var joinedGroup = tgj.getJoinedGroup();

        // Uninitialized lazy groups represent FK-only path navigation that the SQL
        // translator also skips — calling getPrimaryTableReference() would force
        // initialization, emitting a spurious $lookup.
        // Virtual groups (VirtualTableGroup) are synthetic joins not rendered to SQL;
        // skipping them matches Hibernate's own hasRealJoins() semantics, which requires
        // isInitialized() && !isVirtual().
        if (!joinedGroup.isInitialized() || joinedGroup.isVirtual()) {
            continue;
        }

        // Nested table group joins on the joined group mean the ON predicate navigates
        // an association, requiring implicit sub-joins that we cannot translate to the
        // simple $lookup form. Tracked in HIBERNATE-168.
        if (!joinedGroup.getNestedTableGroupJoins().isEmpty()) {
            throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-168 https://jira.mongodb.org/browse/HIBERNATE-168");
        }

        var primaryRef = joinedGroup.getPrimaryTableReference();

        if (primaryRef instanceof FunctionTableReference) {
            throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-111 https://jira.mongodb.org/browse/HIBERNATE-111");
        }
        if (primaryRef instanceof QueryPartTableReference) {
            throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-167 https://jira.mongodb.org/browse/HIBERNATE-167");
        }
        if (!(primaryRef instanceof NamedTableReference joinedNtr)) {
            throw new FeatureNotSupportedException(
                    "Unsupported table reference type: " + primaryRef.getClass().getSimpleName());
        }

        var joinedCollection = joinedNtr.getTableExpression();
        var joinedAlias = joinedNtr.getIdentificationVariable();
        affectedTableNames.add(joinedCollection);

        var preserve = switch (tgj.getJoinType()) {
            case INNER -> false;
            case LEFT  -> true;
            case RIGHT -> throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-161 https://jira.mongodb.org/browse/HIBERNATE-161");
            case FULL  -> throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-162 https://jira.mongodb.org/browse/HIBERNATE-162");
            case CROSS -> throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-163 https://jira.mongodb.org/browse/HIBERNATE-163");
        };

        var fields = extractEquijoinFields(tgj.getPredicate(), joinedAlias);

        // Register before recursing: chained joins need this alias in the set to resolve
        // their localField as a dotted path (e.g. o1_0._id). Safe to do before extraction
        // because extractEquijoinFields uses getColumnExpression() for the foreign side,
        // not resolveFieldPath, so the set state doesn't affect the current join's result.
        joinedTableQualifiers.add(joinedAlias);
        stages.add(new AstLookupStage(joinedCollection, fields.localField(), fields.foreignField(), joinedAlias));
        stages.add(new AstUnwindStage(joinedAlias, preserve));
        stages.addAll(buildJoinStages(joinedGroup));
    }
    return stages;
}
```

**`extractEquijoinFields(Predicate, String joinedAlias)`** — validates and extracts the equijoin field pair:
- `Junction` (compound AND) → throws HIBERNATE-164
- Not a `ComparisonPredicate` with `EQUAL` operator → throws HIBERNATE-165
- Either side not a `ColumnReference` (or `BasicValuedPathInterpretation`) → throws HIBERNATE-166
- Both sides from same table, or neither from joined table → throws descriptively
- Valid → returns `(localField, foreignField)` where:
  - `localField` is the outer/driving side, resolved through `resolveFieldPath` — so in a chained join, `o.id` becomes the dotted path `o1_0._id` in the current pipeline document
  - `foreignField` is the joined-collection side, using `getColumnExpression()` directly (no path prefix) — it refers to a field inside the documents in the joined collection, not a path in the current pipeline document

**`resolveFieldPath(ColumnReference)`** — shared logic used by both `extractEquijoinFields` and `visitColumnReference`:
```java
private String resolveFieldPath(ColumnReference cr) {
    var qualifier = cr.getQualifier();
    return (qualifier != null && joinedTableQualifiers.contains(qualifier))
            ? qualifier + "." + cr.getColumnExpression()
            : cr.getColumnExpression();
}
```

**`visitColumnReference`** — delegates to `resolveFieldPath`:
```java
public void visitColumnReference(ColumnReference columnReference) {
    if (columnReference.isColumnExpressionFormula()) {
        throw new FeatureNotSupportedException("Formula is not supported");
    }
    astVisitorValueHolder.yield(FIELD_PATH, resolveFieldPath(columnReference));
}
```

**`visitSelectClause`** — branches on whether the resolved field path is dotted (joined column) or plain (root column):
```java
var field = acceptAndYield(columnReference, FIELD_PATH);
AstProjectStageSpecification spec = field.contains(".")
        ? new AstProjectStageFieldPathSpecification(field.replace('.', '#'), field)
        : new AstProjectStageIncludeSpecification(field);
projectStageSpecifications.add(spec);
```

Root columns produce `"fieldName": true`; joined columns produce `"alias#column": "$alias.column"`. The `.` check is safe because the validator blocks `.` in mapped field names, so any `.` in a resolved path is always the qualifier separator.

## JOIN FETCH

`JOIN FETCH` in HQL tells Hibernate to eagerly load an association in the same query. From the translator's perspective it produces an identical SQL AST to a plain `JOIN` — the same `TableGroupJoin` with the same `SqlAstJoinType`. The result-set population of the association is handled by Hibernate's result-set processing layer, not the translator. JOIN FETCH therefore requires no special handling and is supported for free once INNER and LEFT joins work.

## Result Reading

`$project` flattens joined-table columns into top-level fields using `#` as the separator between the table alias and the column name. For example, a row from the INNER JOIN example arrives as `{"_id": 1, "o1_0#total": 100}` — a flat document. `MongoResultSet.getValue` calls `BsonDocument.get(key)` directly with the `#`-keyed field name; no path traversal is needed.

The `#` separator is reserved: `MongoAdditionalMappingContributor` validates at `SessionFactory` initialization that no mapped field name contains `.`, `$`, or `#`. Any `#` in a key produced by `visitSelectClause` is therefore always the join-alias separator, never a literal field name character.

For non-join queries the projected keys contain no `#` and the result documents are unchanged flat documents — identical behaviour to before join support was added.

## PluralTableGroup in checkFromClauseSupportability

Hibernate generates collection-loader queries during `SessionFactory` construction for any entity that has a collection-valued attribute. These queries use `PluralTableGroup` as the FROM-clause root (not a regular `StandardTableGroup`), and their model part is `PluralAttributeMapping`, not `EntityPersister`.

The original `checkFromClauseSupportability` check `!(root.getModelPart() instanceof EntityPersister)` therefore rejected all such queries, which had the side-effect of making `buildSessionFactory()` throw `FeatureNotSupportedException` for any entity with an unsupported `@ElementCollection` (e.g., element collections of non-`@Struct` embeddables).

Adding join support required allowing `PluralTableGroup` queries through so that `SessionFactory` construction succeeds for entities with `@OneToMany` and `@Struct` array attributes. To preserve the rejection for unsupported element collections, the check was made more precise:

```
if (root instanceof PluralTableGroup pluralRoot) {
    var elementDescriptor = pluralRoot.getModelPart().getElementDescriptor();
    if (elementDescriptor instanceof EmbeddableValuedModelPart embeddablePart
            && embeddablePart.getEmbeddableTypeDescriptor().getAggregateMapping() == null) {
        // Non-@Struct embeddable element collection — not supported
        throw new FeatureNotSupportedException("TODO-HIBERNATE-169 https://jira.mongodb.org/browse/HIBERNATE-169");
    }
    // Entity collections (@OneToMany/@ManyToMany) and @Struct embeddable arrays — allowed
    if (!(root.getPrimaryTableReference() instanceof NamedTableReference)) {
        throw new FeatureNotSupportedException("Only named table references are supported");
    }
} else {
    // Normal entity query: must be a single-table EntityPersister
    if (!(root.getModelPart() instanceof EntityPersister entityPersister)
            || entityPersister.getQuerySpaces().length != 1) {
        throw new FeatureNotSupportedException("Only single table from clause is supported");
    }
}
```

`getAggregateMapping()` returns non-null only for `@Struct`-annotated embeddables, which are stored as an aggregate BSON document in the parent collection. Non-`@Struct` embeddable element collections map to a secondary table and are rejected.

## Supported and Unsupported Shapes

All unsupported shapes throw `FeatureNotSupportedException`. Shapes that are reasonably implementable in MongoDB MQL reference a Jira ticket; shapes that are outside the MQL join surface get a descriptive message.

| Shape | Disposition | Ticket |
|---|---|---|
| INNER JOIN, single-field equijoin ON | ✅ supported | — |
| LEFT OUTER JOIN, single-field equijoin ON | ✅ supported | — |
| JOIN FETCH (INNER or LEFT) | ✅ supported | transparent |
| Chained joins (A→B→C) | ✅ supported | — |
| RIGHT OUTER JOIN | ❌ throws | HIBERNATE-161 |
| FULL OUTER JOIN | ❌ throws | HIBERNATE-162 |
| CROSS JOIN (null ON predicate) | ❌ throws | HIBERNATE-163 |
| Compound equijoin ON (AND-ed pairs) | ❌ throws | HIBERNATE-164 |
| Non-equijoin ON condition | ❌ throws | HIBERNATE-165 |
| Non-column ON expression | ❌ throws | HIBERNATE-166 |
| Subquery join (`JOIN (SELECT ...) alias ON`) | ❌ throws | HIBERNATE-167 |
| LATERAL UNNEST / function table join | ❌ throws | HIBERNATE-111 |
| ON condition navigates an association (creates implicit sub-joins) | ❌ throws | HIBERNATE-168 |
| Uninitialized lazy join (FK-only path navigation, no join needed) | ✅ silently skipped | — |
| Joins in UPDATE/DELETE | ❌ throws | descriptive message |
| Multiple FROM roots | ❌ throws | existing check (unchanged) |
| DISTINCT | ❌ throws | existing check (unchanged) |

Jira tickets for all unsupported shapes have been filed: HIBERNATE-111, HIBERNATE-161–170.

## Tests

New test class `JoinSelectQueryIntegrationTests` (same package as `SimpleSelectQueryIntegrationTests`). Each positive test asserts the **full MQL pipeline string** and the **full result set**. Seed data uses the same Customer/Order/LineItem entity model already present in the codebase.

### Positive tests

| Test | What it covers |
|---|---|
| `testInnerJoin` | Basic INNER JOIN; verifies `$lookup` + `$unwind` in MQL |
| `testInnerJoinWithWhereOnJoinedTable` | WHERE on joined-table column (e.g., `o.total > 100`) |
| `testInnerJoinWithWhereOnRootTable` | WHERE on root-table column |
| `testLeftOuterJoin` | LEFT JOIN with at least one unmatched outer row; asserts null fields in result |
| `testJoinFetchManyToOne` | `@ManyToOne` association with `JOIN FETCH`; verifies association populated |
| `testJoinFetchOneToMany` | `@OneToMany` association with `JOIN FETCH` |
| `testThreeWayInnerJoin` | Chained A→B→C joins; verifies dotted `localField` in second `$lookup`; exercises `buildJoinStages` recursion |
| `testTwoJoinsFromSameEntity` | Two sibling joins from same entity (`JOIN o.customer c JOIN o.lineItems li`); exercises the `buildJoinStages` loop with 2 iterations |
| `testOrderByJoinedColumn` | ORDER BY on a joined-table column |

### Negative tests

Most negative tests assert that executing the query throws `FeatureNotSupportedException`. Two tests document cases where Hibernate's own internals intercept the query before translation reaches our throw site:

- `testLateralUnnestThrows` (`FROM OrderWithArray o JOIN o.items i`): In Hibernate 7.3, `getSetReturningFunctionDescriptor("unnest")` returns null because the MongoDB dialect does not register an unnest set-returning function descriptor — the query fails at HQL semantic translation with `InterpretationException` before our translator is reached. The HIBERNATE-111 `FeatureNotSupportedException` throw in `buildJoinStages` is unreachable via HQL. (Verified: registering `"unnest"` in the dialect causes the query to reach our `FunctionTableReference` guard and throw correctly.)
- `testSubqueryJoinThrows` (`JOIN (SELECT ...) ord ON ...`): The HQL parser rejects this syntax with `SemanticException` before translation. The HIBERNATE-167 throw is unreachable via HQL.

Both tests are kept to document the actual behavior and serve as regression guards if Hibernate changes its handling of these constructs.

For `testCompoundOnConditionThrows`, the HQL `JOIN Order o ON c.id = o.id AND c.id < 100` uses a compound ON condition with only plain column references (no association navigation). This ensures the `Junction` branch in `extractEquijoinFields` is reached and throws HIBERNATE-164 before the association-navigation check (HIBERNATE-168) fires.

| Test | Shape rejected | HQL approach | Exception |
|---|---|---|---|
| `testRightJoinThrows` | RIGHT OUTER JOIN | `FROM Customer c RIGHT JOIN Order o ON ...` | `FeatureNotSupportedException` (HIBERNATE-161) |
| `testFullJoinThrows` | FULL OUTER JOIN | `FROM Customer c FULL JOIN Order o ON ...` | `FeatureNotSupportedException` (HIBERNATE-162) |
| `testCrossJoinThrows` | CROSS JOIN (null predicate) | `FROM Customer c CROSS JOIN Order o` | `FeatureNotSupportedException` (HIBERNATE-163) |
| `testNonEquijoinThrows` | Non-equality ON condition | `JOIN Order o ON c.id > o.total` | `FeatureNotSupportedException` (HIBERNATE-165) |
| `testCompoundOnConditionThrows` | Compound ON condition (AND-ed predicates) | `JOIN Order o ON c.id = o.id AND c.id < 100` | `FeatureNotSupportedException` (HIBERNATE-164) |
| `testNonColumnOnExpressionThrows` | Non-`ColumnReference` ON expression | `JOIN Order o ON c.id = o.total + 1` (arithmetic in ON clause) | `FeatureNotSupportedException` (HIBERNATE-166) |
| `testLateralUnnestThrows` | LATERAL UNNEST (function table join) | `FROM OrderWithArray o JOIN o.items i` | `InterpretationException` (Hibernate 7.3 SQM translation, HIBERNATE-111 unreachable) |
| `testSubqueryJoinThrows` | Subquery/derived-table join | `SELECT c.id FROM Customer c JOIN (SELECT o.id FROM Order o) ord ON c.id = ord.id` | `SemanticException` (HQL parser, HIBERNATE-167 unreachable) |
| `testOnConditionWithAssociationNavigationThrows` | ON navigates association (creates nested implicit sub-join) | `SELECT c.id FROM Customer c JOIN c.orders o ON o.customer.name = 'Alice'` | `FeatureNotSupportedException` (HIBERNATE-168) |
| `testEntitySyntaxJoinThrows` | Entity join syntax where ON navigates association (e.g. `ON c = o.customer`) | `SELECT c.id, o.total FROM Customer c JOIN Order o ON c = o.customer` | `FeatureNotSupportedException` (HIBERNATE-168) |

## Commit and PR Strategy

**Before writing any code:** Jira tickets for all unsupported shapes are already filed. Use the real ticket numbers in the throw messages from the start.

**Single implementation PR** covering the complete feature:
- `AstLookupStage`, `AstUnwindStage`, and `AstProjectStageFieldPathSpecification` (new AST nodes)
- All `AbstractMqlTranslator` changes (`buildJoinStages`, `extractEquijoinFields`, `resolveFieldPath`, `visitColumnReference`, `visitSelectClause`, `visitFromClause`, mutation guard)
- `MongoStatement.isExcludeProjectSpecification` — relaxed to accept single-`$` field-path strings; error message updated
- `MongoAdditionalMappingContributor` — `#` added to `UNSUPPORTED_FIELD_NAME_CHARACTERS`
- `JoinSelectQueryIntegrationTests` — test class restructured (positive tests hoisted to outer class, negative tests wrapped in `Unsupported` nested class) to match module conventions
- All integration tests: INNER JOIN, LEFT OUTER JOIN, JOIN FETCH, chained joins, all negative tests
- `IdentifierIntegrationTests` — `#` character tests added

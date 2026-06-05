# HIBERNATE-67: Translate EXISTS-over-UNNEST to $elemMatch

## Background

HQL supports an implicit unnest syntax where `WHERE EXISTS (FROM c.lineItems li WHERE li.sku = 'WIDGET-1')` desugars to an EXISTS predicate over a UNNEST function table reference in the SQL AST. This preserves parent cardinality (one result row per matching Cart regardless of how many line items match), unlike the JOIN pattern which multiplies rows.

The current translator throws `FeatureNotSupportedException` for all EXISTS predicates.

## MongoDB Mechanism

EXISTS-over-unnest maps entirely within the existing `$match` stage — no new pipeline stages are required. All five shapes verified in mongosh:

**Simple equality:**
```json
{ "$match": { "lineItems": { "$elemMatch": { "sku": "WIDGET-1" } } } }
```

**Compound AND** (the translator emits explicit `$and` via `AstLogicalFilter`):
```json
{ "$match": { "lineItems": { "$elemMatch": { "$and": [{ "sku": "WIDGET-1" }, { "qty": { "$gt": 2 } }] } } } }
```

**OR in body:**
```json
{ "$match": { "lineItems": { "$elemMatch": { "$or": [{ "sku": "WIDGET-1" }, { "qty": { "$gt": 5 } }] } } } }
```

**NOT EXISTS** — wraps in `$nor`, consistent with how `NegatedPredicate` is handled elsewhere:
```json
{ "$match": { "$nor": [{ "lineItems": { "$elemMatch": { "sku": "WIDGET-1" } } }] } }
```

**No WHERE body** (`EXISTS (FROM c.lineItems li)`) — matches documents with at least one array element:
```json
{ "$match": { "lineItems": { "$elemMatch": {} } } }
```

## Pipeline Structure

No new pipeline stages. The `$elemMatch` filter is yielded by `visitExistsPredicate` and composed into the same `AstMatchStage` as any other WHERE predicate.

## Implementation Approach

### New AST Node: `AstElemMatchFilterOperation`

A new record in `mongoast/filter/`, following the pattern of `AstComparisonFilterOperation`:

```java
public record AstElemMatchFilterOperation(AstFilter body) implements AstFilterOperation {
    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        writer.writeName("$elemMatch");
        body.render(writer);
        writer.writeEndDocument();
    }
}
```

The body is always non-null. Pass `AstEmptyFilter.INSTANCE` (renders as `{}`) for the no-WHERE case.

### Prerequisite: Register `unnest` in `MongoDialect`

`FROM c.lineItems li` inside a subquery requires Hibernate's HQL parser to resolve `unnest` as a set-returning function (SRF). Without this registration, `SqmCriteriaNodeBuilder.unnestArray` NPEs before the translator is ever invoked.

Add to `MongoDialect.initializeFunctionRegistry()`:

```java
functionRegistry.register("unnest", new MongoUnnestFunction());
```

`MongoUnnestFunction` extends `AbstractSqmSelfRenderingSetReturningFunctionDescriptor` directly, passing `UnnestSetReturningFunctionTypeResolver("value", "ordinality")` to the constructor for HQL type resolution. Its `render` method calls `fail()` — an assertion error — because rendering is unreachable: `recognizeExistsOverUnnest` intercepts the `FunctionTableReference` directly without dispatching through `visitFunctionTableReference`.

### Changes to `AbstractMqlTranslator`

**`visitExistsPredicate`** replaces the current `throw new FeatureNotSupportedException()`:

1. Call `recognizeExistsOverUnnest(existsPredicate)` → `ExistsOverUnnestShape(arrayFieldName, innerAlias, bodyPredicate)` or null
2. If null → throw `FeatureNotSupportedException("TODO-HIBERNATE-178 ...")` (EXISTS over non-unnest subquery)
3. If `bodyPredicate != null`: save `elemMatchInnerAlias`, set to `shape.innerAlias()`, call `acceptAndYield(bodyPredicate, FILTER)`, restore in `finally`; else use `AstEmptyFilter.INSTANCE`
4. Build `new AstFieldOperationFilter(arrayFieldName, new AstElemMatchFilterOperation(bodyFilter))`
5. If `existsPredicate.isNegated()` → wrap in `new AstLogicalFilter(NOR, List.of(filter))`
6. Yield result

**`recognizeExistsOverUnnest(ExistsPredicate)`** — static helper, returns null on any mismatch:
- Unwrap: `ExistsPredicate` → `SelectStatement` → `QueryPart`; reject if not `QuerySpec`
- Reject if `QuerySpec` has GROUP BY, ORDER BY, OFFSET, or FETCH
- Reject if `fromClause` has anything other than exactly one root with no joins
- Reject if root's primary table reference is not `FunctionTableReference` named `"unnest"`
- Extract array field name from the unnest argument (`BasicValuedPathInterpretation` only)
- Return `ExistsOverUnnestShape(arrayFieldName, innerAlias, querySpec.getWhereClauseRestrictions())`

Note: `visitExistsPredicate` accesses `existsPredicate.getExpression().getQueryPart()` directly rather than dispatching through `accept()`, since `visitSelectStatement` throws for non-root queries.

**`visitColumnReference`** — augmented with correlated outer-ref detection:

When `elemMatchInnerAlias` is set (i.e., we are translating an `$elemMatch` body), check whether the column reference's qualifier differs from `innerAlias`. If so, throw HIBERNATE-177. This check fires for any predicate type the translator handles, covering all future predicate additions automatically.

`BasicValuedPathInterpretation.accept()` delegates to `columnReference.accept()`, so this check covers both `ColumnReference` and `BasicValuedPathInterpretation` field paths.

**`elemMatchInnerAlias`** — instance field `@Nullable String`:

Set to the unnest table group's identification variable before translating the `$elemMatch` body, restored to its previous value in `finally`. The save/restore handles future nested EXISTS-over-unnest cases correctly.

**No changes** to existing predicate visitors. The body translates through `visitJunction`, `visitRelationalPredicate`, `visitNegatedPredicate`, etc. unchanged.

## Supported and Unsupported Shapes

| Shape | Verdict | Ticket |
|-------|---------|--------|
| `EXISTS (FROM c.arr a WHERE a.f = :v)` | ✅ | HIBERNATE-67 |
| `EXISTS (FROM c.arr a WHERE a.f = :v AND a.g > :w)` | ✅ | HIBERNATE-67 |
| `EXISTS (FROM c.arr a WHERE a.f = :v OR a.g > :w)` | ✅ | HIBERNATE-67 |
| `EXISTS (FROM c.arr a WHERE NOT (a.f = :v))` | ✅ | HIBERNATE-67 |
| `NOT EXISTS (FROM c.arr a WHERE a.f = :v)` | ✅ | HIBERNATE-67 |
| `EXISTS (FROM c.arr a WHERE a.b)` — bare boolean | ✅ | HIBERNATE-67 |
| `EXISTS (FROM c.arr a)` — no WHERE | ✅ | HIBERNATE-67 |
| `EXISTS (FROM c.arr a WHERE a.qty > c.minQty)` — correlated outer ref | ❌ throw | HIBERNATE-177 |
| All other EXISTS shapes (over entity, GROUP BY, UNION, etc.) | ❌ throw | HIBERNATE-178 |

Note: predicate types not yet supported (LIKE, IS NULL, BETWEEN, etc.) are not listed as ❌ — they will work inside `$elemMatch` bodies automatically when added to the translator, since the same visitor infrastructure is used.

## Tests

**Test class:** `ExistsSelectQueryIntegrationTests`

**Entity model:** `Cart` (`@Entity`, fields: `id`, `minQty`, `status` String) with `LineItem[]` (`@Embeddable @Struct`, fields: `sku`, `qty`, `active` boolean)

**Positive tests** (outer class, `@BeforeEach` seed data):

| Test | HQL | Covers |
|------|-----|--------|
| `testSimpleEquality` | `FROM Cart c WHERE EXISTS (FROM c.lineItems li WHERE li.sku = :sku)` | core path, single ComparisonPredicate |
| `testConjunction` | `... WHERE li.sku = :sku AND li.qty > :qty` | Junction AND, multi-field $elemMatch |
| `testDisjunction` | `... WHERE li.sku = :sku OR li.qty > :qty` | Junction OR, $or in body |
| `testNegatedBodyPredicate` | `... WHERE NOT (li.sku = :sku)` | NegatedPredicate in body |
| `testNotExists` | `FROM Cart c WHERE NOT EXISTS (FROM c.lineItems li WHERE li.sku = :sku)` | `isNegated()=true`, outer $nor |
| `testBooleanField` | `... WHERE li.active` | BooleanExpressionPredicate |
| `testNoWhereBody` | `FROM Cart c WHERE EXISTS (FROM c.lineItems li)` | null body → `$elemMatch: {}` |
| `testExistsComposedWithOuterPredicate` | `FROM Cart c WHERE c.status = :status AND EXISTS (FROM c.lineItems li WHERE li.sku = :sku)` | $elemMatch composes with other filters in outer $match |

**Negative tests** (`@Nested class Unsupported`):

| Test | HQL | Covers |
|------|-----|--------|
| `testCorrelatedOuterRef` | `FROM Cart c WHERE EXISTS (FROM c.lineItems li WHERE c.status = 'shipped')` | outer field ref in body → `visitColumnReference` throws HIBERNATE-177 |
| `testExistsOverEntity` | `FROM Cart c WHERE EXISTS (FROM Cart c2 WHERE c2.id = :id)` | non-unnest EXISTS → throw HIBERNATE-178 |

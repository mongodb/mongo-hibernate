# HIBERNATE-82 follow-up: GROUP BY expression keys

> **This document is for FUTURE implementation only.** The current shipped
> slice (HIBERNATE-82) supports GROUP BY with `ColumnReference` keys only.
> Everything described here is deferred work.

## Summary

Extend GROUP BY translation to support **expression keys** (arithmetic, unary
operations, later function calls) — not just `ColumnReference` keys. Add
correct handling of SELECT / ORDER BY / HAVING references to those keys,
including composite expressions like `x + 1` referenced from `x + 1 + y`.

Companion to `2026-07-20-hibernate-groupby-design.md`, which locked in the
initial column-only slice.

## Problem

Hibernate hands us SELECT and GROUP BY clauses as independent Expression
trees. `SELECT x + 1 ... GROUP BY x + 1` produces two separate
`BinaryArithmeticExpression` instances. To emit valid MQL, we must recognize
that the SELECT item is a grouped key and rewrite it to `$_id.<subKey>` rather
than recomputing against source fields that no longer exist after `$group`.

For column-only keys the current code walks `resolveFieldPath` and looks up
the `ColumnReference` in a map. For expression keys the visitor recurses into
the composite and hits leaf columns (`x`) that alone are not group keys — the
lookup misses and either throws prematurely (breaking valid queries) or
silently emits references to fields that no longer exist (breaking
correctness).

## Design choice: IR walk on translated `AstExpression`

Our `AstExpression` hierarchy is already an intermediate representation
between Hibernate's SQL AST and BSON. Records give structural `equals` for
free. Two variants of the same idea are worth documenting:

### Variant A — post-translation walk

1. Translate the SELECT / ORDER BY / HAVING item to `AstExpression` in one
   pass (existing visitor path, unchanged).
2. At the top-level entry, look up the translated expression in
   `Map<AstExpression, String>` (group-key → sub-key). Whole match →
   rewrite to `AstFieldPathExpression("_id." + subKey)`.
3. Otherwise walk the produced `AstExpression` tree once:
   - Any node matching the map is rewritten in place.
   - Any `AstFieldPathExpression` not in `_id.*` after all rewrites → throw
     ("column not in GROUP BY", listing the leaf field paths).

Requires an `AstExpression.children()` default method and one `children()` /
`withChildren(...)` override per composite record (5–6 records today). Adds
one small tree-walk pass in the translator.

**Note — single pass requires the same checkpoint/counter as Variant B.**
A naive "recurse rewrite, then scan for strays" is two passes. To do it in
one, the walker maintains a `strays` list with checkpoints: on entering a
subtree, capture `cp = strays.size()`; after recursing, if the subtree
whole-matched a group key, `restore(cp)` (its strays are subsumed);
otherwise leaf `AstFieldPathExpression`s not in `_id.*` accumulate. At the
top, throw if `!strays.isEmpty()`. So the checkpoint machinery is not
unique to Variant B — the two variants share the same algorithm; only the
tree being walked differs (translated `AstExpression` vs Hibernate SQL AST
during visit).

### Variant B — stray tracking during translation

Rewrite at each yield site during the existing visitor pass, using a stray
tracker to defer error decisions until the top of the SELECT / ORDER BY /
HAVING item is reached.

```java
static final class GroupByContext {
    // existing phase + exprMappings (now keyed on AstExpression)

    private final List<AstFieldPathExpression> strays = new ArrayList<>();

    int checkpoint()                          { return strays.size(); }
    void restore(int cp)                      { while (strays.size() > cp) strays.remove(strays.size() - 1); }
    void noteStray(AstFieldPathExpression e)  { strays.add(e); }
    void resetStrays()                        { strays.clear(); }
    List<AstFieldPathExpression> strays()     { return strays; }

    @Nullable String subKeyFor(AstExpression e) { return exprMappings.get(e); }
}
```

Callers:

- **`visitColumnReference`**: build raw `AstFieldPathExpression`. If
  `subKeyFor(raw) != null` → yield `AstFieldPathExpression("_id." + subKey)`.
  Else (in group scope) → `groupByContext.noteStray(raw)` and yield raw.

- **Composite visit** (`visitBinaryArithmeticExpression`, etc.):
  ```
  int cp = groupByContext.checkpoint();
  ... build raw composite ...
  String subKey = groupByContext.subKeyFor(raw);
  if (subKey != null) {
      groupByContext.restore(cp);   // whole subtree covered
      yield AstFieldPathExpression("_id." + subKey);
  } else {
      yield raw;                     // strays stay
  }
  ```

- **Top-level SELECT / ORDER BY / HAVING** item loop:
  ```
  groupByContext.resetStrays();
  var raw = acceptAndYield(item, EXPRESSION);
  if (!groupByContext.strays().isEmpty()) {
      throw new FeatureNotSupportedException(
          "Columns that are not part of group by: " + groupByContext.strays());
  }
  ```

No second walk. Strays are cleared when a wholesale match subsumes their
subtree.

### Worked examples

Both variants produce the same MQL for these cases.

- `SELECT x + 1 ... GROUP BY x`
  → root `AstBinaryOp(add, $x, 1)` — no match.
  Leaves: `$x` matches → `_id.x`. Literal `1` — safe.
  Result: `AstBinaryOp(add, "_id.x", 1)`. No strays.

- `SELECT x + 1 ... GROUP BY x + 1`
  → root matches → replace with `_id.k0`. No strays.

- `SELECT x + y ... GROUP BY x`
  → root not a match. `$x` matches; `$y` → stray.
  Result contains `$y` → **throw** listing `y`.

- `SELECT (x + 1) * 2 ... GROUP BY x + 1`
  → outer `*` not a match. Recurse into left: `x + 1` matches → `_id.k0`.
  Right: literal `2` safe.
  Result: `AstBinaryOp(mul, "_id.k0", 2)`. No strays.

## Comparison

| | Variant A (walk) | Variant B (stray tracking) |
|---|---|---|
| Traversals per item | 1 translation + 1 verify walk | 1 translation only |
| Coupling in visitors | Minimal (visitors unchanged) | Each composite adds checkpoint discipline |
| Encapsulation | Walker is its own method | `GroupByContext` owns the state |
| Extensibility | Add `children()` per new type | Add checkpoint block in each new composite visit |
| Error message quality | Same (list stray field paths) | Same |

Both are O(N). Both scale to any expression subclass we support today or add
later. **Variant B (stray tracking) is the current recommendation** — single
pass, encapsulated in `GroupByContext`, and integrates naturally with the
existing visitor pattern the translator already uses.

## Scope for the follow-up

- Support expression keys: `BinaryArithmeticExpression`, `UnaryOperation`,
  `QueryLiteral` / `JdbcLiteral`, `SqlSelectionExpression`.
- `SelfRenderingFunctionSqlAstExpression` **remains blocked** by HIBERNATE-196.
  When HIBERNATE-196 lifts, function keys will fall out for free via the
  same stray-tracking mechanism.
- Sub-key naming for non-column keys: positional (`k0`, `k1`, …) since there
  is no natural derived name.
- HAVING already uses the same infrastructure (already merged in the initial
  slice) — expression-key support in HAVING falls out because HAVING is a
  top-level entry point for stray tracking.
- ORDER BY over an expression key: same top-level entry point; rewrite pass
  emits `$sort: {"_id.k0": 1}`.

## Tests (new integration cases)

- `GROUP BY x + 1` — positive.
- `SELECT x + 1 ... GROUP BY x + 1 HAVING x + 1 > n` — positive.
- `SELECT (x + 1) * 2 ... GROUP BY x + 1` — nested composite over key.
- `GROUP BY -x` — unary key.
- `SELECT x + y ... GROUP BY x` — negative, throws listing `y`.
- `SELECT y ... GROUP BY x + 1` — negative, throws listing `y`.
- `GROUP BY YEAR(dob)` — throws (HIBERNATE-196), unchanged from today.

Every positive test asserts the full MQL pipeline string.

## Implemented design — holder-level Rewriter attachment

Variant B was implemented with the checkpoint/stray machinery attached to
the yield holder rather than scattered across composite visitors.

### Shape

`AstVisitorValueHolder` gains a nullable `ExpressionRewriter`:

```java
public interface ExpressionRewriter {
    @Nullable AstExpression rewriteExpression(AstExpression yielded);
    @Nullable String rewriteFieldPath(String yielded);
    int checkpoint();
    void restoreSince(int cp);
    void onScopeExit();          // may throw
    default void noteStray(String rawFieldPath) {}
}
```

`execute(descriptor, rewriter, runnable)` is the intercept point. Before
running, it records the previous rewriter and captures `cp` if a rewriter
is installed and the descriptor is EXPRESSION or FIELD_PATH. After
`runnable` yields a value, `execute` calls the descriptor-appropriate
rewrite method; on a non-null result it replaces the yielded value and
calls `restoreSince(cp)`. On scope exit (finally), the previous rewriter
is restored and, if this frame installed a rewriter, `onScopeExit()` is
called (throws if strays remain).

`acceptAndYield(node, descriptor)` and `acceptAndYieldExpression(expr)`
keep their existing 2-arg signatures as overloads that delegate to a
3-arg form; only the three top-level installation sites use the 3-arg
form. Internal visitors are unchanged.

### Where the rewriter is installed

- `visitSelectClause` — per `SqlSelection`.
- `createSortStage` — per `SortSpecification`.
- `createMatchStage` — internally installs when `isAfterGroup()` (WHERE
  runs pre-population and passes null; HAVING runs post-population and
  installs a rewriter).

Each installation constructs a fresh `GroupExpressionRewriter`, backed by
the map from `GroupByContext.astKeyMappings()`. Strays live on the
rewriter instance — `GroupByContext` no longer holds mutable state
between scopes.

### Group-key map

Keyed on `AstNode` (wider than `AstExpression`) so both descriptor paths
converge on a single lookup:

- `rewriteExpression(AstExpression y)` → `keyMap.get(y)`.
- `rewriteFieldPath(String path)` → `keyMap.get(new AstFieldPathExpression(path))`.

Column keys and expression keys populate the same map during `$group`
construction. Column sub-key is the fieldPath with `.` → `#`; expression
sub-key is positional `k<i>`.

### Stray tracking

`resolveFieldPath` in AFTER_GROUP always returns the raw column path and,
if a rewriter is installed, calls `currentRewriter().noteStray(rawPath)`.
When an outer composite wholesale-matches, its `restoreSince(cp)` drops
strays that were logged during descent. Strays surviving to
`onScopeExit()` are real errors.

### Worked examples

**Column key + column SELECT** — `SELECT x FROM t GROUP BY x`
- Map: `AstFieldPathExpression("x") → "x"`.
- SELECT column yields FIELD_PATH `"x"`. Rewriter's `rewriteFieldPath` wraps and looks up → matches → returns `"_id.x"`.
- MQL: `[{$group: {_id: {x: "$x"}}}, {$project: {"_id#x": "$_id.x"}}]`.

**Expression key, wholesale match** — `SELECT x + 1 FROM t GROUP BY x + 1`
- Map: `AstBinaryOp(add, $x, 1) → "k0"`.
- Enter outer `+`. Descend `x` → leaf `$x`: no map hit; `noteStray("x")`.
- Right literal 1. Outer assembles `AstBinaryOp(add, $x, 1)`; rewriter matches → replaces with `AstFieldPathExpression("_id.k0")`; `restoreSince(cp)` drops the "x" stray.
- MQL: `[{$group: {_id: {k0: {$add: ["$x", 1]}}}}, {$project: {"_c_0": "$_id.k0"}}]`.

**Expression key, leaf rewrite via column** — `SELECT x + 1 FROM t GROUP BY x`
- Map: `AstFieldPathExpression("x") → "x"`.
- Enter outer `+`. Descend `x` → matches leaf → `_id.x`.
- Right literal 1. Outer assembles `AstBinaryOp(add, $_id.x, 1)`; no wholesale match.
- MQL: `[{$group: {_id: {x: "$x"}}}, {$project: {"_c_0": {$add: ["$_id.x", 1]}}}]`. Semantically equivalent to previous case.

**Stray survives** — `SELECT x + y FROM t GROUP BY x`
- Map: `$x → "x"`.
- Enter outer `+`. `x` matches → `_id.x`; `y` → no match, `noteStray("y")`; literal — n/a. Outer assembles `AstBinaryOp(add, $_id.x, $y)`; no match.
- `onScopeExit()` sees `["y"]` → throws `Columns that are not part of GROUP BY: [y]`.

**HAVING over expression key** — `SELECT x + 1 ... GROUP BY x + 1 HAVING x + 1 > 1`
- Same map as wholesale-match case. The HAVING predicate translates its comparison operands via EXPRESSION; the `x + 1` operand matches → `_id.k0`.
- MQL emits `{$match: {$expr: {$gt: ["$_id.k0", 1]}}}` (or the field-form when the rewriter yields `$_id.k0` as a field path).

### Issues / known limitations

- **Bottom-up misses wholesale outer matches when inner leaves are also
  group keys.** See the next section — not a correctness bug, but a
  materialization cost concern for large composite keys.
- **`ExpressionRewriter` handles two yield types (`AstExpression` and
  `String`) via separate methods.** Because FIELD_PATH yields a raw
  `String` (used as a BSON key name by downstream `AstFieldOperationFilter`,
  `AstProjectStageFieldPathSpecification`, `AstSortField`, etc.), the
  rewriter has to wrap-then-lookup for FIELD_PATH matches. Not elegant but
  unavoidable without a wider refactor of FIELD_PATH's yield type.
- **Nested scopes are not exercised.** Current top-level sites (SELECT
  item, sort spec, HAVING predicate) do not nest. If a future feature
  (correlated subquery with its own GROUP BY) introduces nested scopes,
  the prev/restore of `rewriter` in `execute` handles it correctly, but
  this path is unverified.
- **`SelfRenderingFunctionSqlAstExpression` (function calls) remains
  blocked in EXPRESSION mode by HIBERNATE-196.** Function keys therefore
  cannot yet be used in GROUP BY; when HIBERNATE-196 lifts, function keys
  fall out for free because the map is keyed on translated `AstNode` and
  the rewriter runs at every EXPRESSION yield.

## Bottom-up vs top-down matching — canonicalization cost

Variant B rewrites at yield-time, which is bottom-up. Leaves are rewritten
before composites are assembled. Consequence: an outer wholesale match can
be missed when its inner columns are ALSO group keys, because by the time
the composite is assembled its inner nodes are already `_id.<colSubKey>` —
so the assembled composite no longer matches its own map entry (which
holds the raw `$col` form).

Example: `SELECT x + 1, x FROM t GROUP BY x, x + 1`
- Map after `$group`:
  `AstFieldPathExpression("x") → "x"`, `AstBinaryOp(add, $x, 1) → "k1"`.
- SELECT item 1: `x + 1`
  - Enter outer `+`. Descend `x` → matches leaf → `_id.x`.
  - Right literal `1`. Outer assembles `AstBinaryOp(add, "_id.x", 1)`.
  - Rewriter lookup on assembled form: the map holds
    `AstBinaryOp(add, "x", 1)` — the inner is `"x"`, not `"_id.x"` —
    **no match**.
  - Emitted `$project` value: `{$add: ["$_id.x", 1]}` instead of
    `"$_id.k1"`.
- SELECT item 2: `x` → matches leaf directly → `"$_id.x"`.
- Final `$project`:
  `{"_c_0": {$add: ["$_id.x", 1]}, "_c_1": "$_id.x"}`
  instead of the canonical
  `{"_c_0": "$_id.k1", "_c_1": "$_id.x"}`.

Both `k1` and `x + 1` were already evaluated per input row during
`$group`; the bottom-up form makes `$project` recompute `x + 1` per
output group. Equivalent value, extra work — see cost discussion below.

**Not a correctness bug.** `$_id.x + 1 ≡ $_id.k1` for every group; every
downstream operator (HAVING, ORDER BY, projection) sees identical results.
SQL semantics require only that a SELECT expression be functionally
derivable from group keys — either form is valid, and no relational
planner guarantees which is chosen. Aligned with standard behavior.

**Cost model.** The overhead is per-group, not per-row, because `$group`
already collapsed N input rows to G groups. `$project` runs G times, so
recomputing the expression is G extra evaluations — trivial for simple
expressions. For a large composite group key with many terms (e.g., a
100-term formula), G extra full-formula recomputations may be undesirable.
Not a correctness issue but a materialization efficiency one.

**Top-down alternative (Variant A resurfacing).** To always prefer the
wholesale match, translate the item raw first (rewriter disabled), then
run a single top-down walk on the assembled `AstExpression`: at each node
consult the map, replace-and-stop on match, otherwise recurse into
children. Requires `AstExpression.children()` / `withChildren(...)`
overrides on the composite records (5–6 today) and integrates stray
detection into the same walk. If group-key sharing between SELECT/ORDER
BY/HAVING and `$group._id` becomes a meaningful cost, revisit.

**Bottom-up single-pass alternative (dual-form).** Instead of a second
walk, carry both forms through translation. Two variants:

- **B1 — Rewriter-owned shadow map**: visitors always yield the **raw**
  form (no substitution). Rewriter maintains
  `IdentityHashMap<AstExpression, AstExpression> shadow`. On each yield,
  the rewriter (a) checks raw against the group-key map for wholesale
  match → shadow = `AstFieldPathExpression("_id.<subKey>")`;
  (b) otherwise for composites, computes shadow by looking up each child
  in the shadow map and reassembling via `withChildren(...)`. At the
  scope's top-level, emit `shadow[outermostRaw]`.

  **Pitfall**: this depends on the invariant "every AstExpression that
  ends up as a child of another has itself been yielded through the
  holder, so it has a shadow entry." Real visitors break this — e.g.
  `visitUnaryOperationExpression` UNARY_MINUS constructs
  `AstBinaryOp(MULTIPLY, new AstValueExpression(-1), operand)` where
  `AstValueExpression(-1)` is inline and never yielded; the
  DIVIDE-with-cast wrapper similarly builds an inline intermediate
  composite. Reassembling via `shadow::get` returns `null` for those and
  loses any rewrites inside them.

  Resolutions:

  1. **Yield every intermediate**: restructure visitors to route each
     intermediate node through the holder so it gets a shadow entry.
     Invasive; every future visitor must remember this rule.
  2. **Lazy recursive shadow lookup**: `shadow.get(node)` becomes a
     memoized function — on miss, if the node is a composite, recurse
     into `children()`, look up (recursively) each, and reassemble via
     `withChildren(...)`; on miss for a leaf, identity. This requires
     `children()` + `withChildren(...)` on every composite that can hold
     `AstExpression` children — same requirement as the IR walk. Cost
     is amortized O(N) via memoization. The invariant then becomes
     "composites expose children" (enforced by the interface) rather
     than "every intermediate is yielded" (scattered discipline).

- **B2 — Visitor-driven dual yield**: the yield type widens to
  `Pair(raw, rewritten)`. Every composite visitor assembles both trees
  in parallel from child pairs, and the rewriter overrides `rewritten`
  when the raw matches a group key. No `children()` required, no
  invariant on intermediate yielding. Cost: every EXPRESSION-producing
  visitor gets an extra assembly line; the yield type change ripples
  through composites and their consumers.

Both B1 (resolution 2) and B2 are single-pass, bottom-up, O(N). B1
localizes the change to the rewriter and AST records; B2 localizes it to
visitors. Pick when we decide to invest in canonicalization.

## Optimized approach: Structural Value Numbering

### Why naive structural comparison is O(N²)

Hibernate builds the GROUP BY and SELECT expression trees independently.
`SELECT x + 1 ... GROUP BY x + 1` produces two separate
`BinaryArithmeticExpression` instances with no shared pointers. Object
identity (`==`) does not work. Structural comparison at each composite
node during translation requires walking the subtree — O(subtree_size × K)
per node, O(N² × K) for the whole SELECT expression tree where N = number
of nodes and K = number of group keys.

Why compilers avoid this: compilers share AST nodes — the same `x + 1`
object appears in both GROUP BY and SELECT. Equality is `==`. That sharing
is established during semantic analysis by looking up the expression in the
SELECT list (Hibernate does this for aliases and positional refs via
`SqmAliasedNodeRef`, but not for explicit expressions — see
`BaseSqmToSqlAstConverter.resolveGroupOrOrderByExpression`, line 2629:
`groupByClauseExpression.accept(this)` builds a fresh SQL AST node).

### Value Numbering

Production compilers (LLVM GVN, GCC SCCVN) solve the equivalence problem
with **structural value numbering** (Alpern, Wegman, Zadeck 1988): assign
each distinct expression a canonical integer (**value number, VN**) such
that two expressions have the same VN if and only if they are structurally
identical (given semantic leaf identity).

The algorithm is bottom-up (post-order). Each node produces an
**expression key** — a tuple of its opcode and its children's VNs — and
looks up or inserts that key in a global hash table that assigns
auto-incremented integers:

```java
sealed interface ExprKey {}
record ColumnKey(String fieldPath)                           implements ExprKey {}
record LiteralKey(Object value)                              implements ExprKey {}
record CompositeKey(String opcode, int leftVN, int rightVN)  implements ExprKey {}

// Global tables (shared across GROUP BY and SELECT passes)
Map<ExprKey, Integer> vnTable  = new HashMap<>();
int nextVN = 0;

int vn(Expression e) {
    ExprKey key = switch (e) {
        case ColumnReference c  -> new ColumnKey(c.resolvedFieldPath());
        case QueryLiteral l     -> new LiteralKey(l.getLiteralValue());
        case BinaryArithmetic b -> new CompositeKey(b.operator().name(),
                                       vn(b.getLeftHandOperand()),
                                       vn(b.getRightHandOperand()));
        // ...
    };
    return vnTable.computeIfAbsent(key, k -> nextVN++);
}
```

The critical property: because `ColumnKey("x")` is the same Java record
regardless of which `ColumnReference` object produced it, both GROUP BY's
`x` and SELECT's `x` map to the same VN. Their parent composites therefore
produce the same `CompositeKey` and receive the same VN — without any
tree-vs-tree comparison.

**Cost:** O(k) per node where k = number of immediate children; O(N) for
the whole expression tree. Hash table lookup is expected O(1) after hashing
the key (Java `HashMap` handles collisions by full key equality, not by
relying on hash uniqueness).

**Hash-consing** is a related technique used in functional compilers (GHC):
instead of assigning a number, `cons(op, children)` interns the node
itself in a global table so the same expression is the same object. VN is
the query-oriented analogue — same structure → same integer; does not
require immutable nodes.

### Two-pass algorithm with selective top-down replacement

Naively applying VNs bottom-up during translation reintroduces the
canonicalization miss: if `x` is also a group key, it is rewritten to
`$_id.x` before the parent `x + 1` is assembled, so the parent's VN no
longer matches the stored GROUP BY key VN (see "canonicalization cost"
section). Fix: separate VN computation from translation.

**Pass 1 — bottom-up VN computation (pre-order on the way up):**
Walk the Hibernate SQL AST expression tree post-order. At each node,
compute its VN and record matches: if `groupKeyVN.containsKey(vn)`, add
`(node, subKey)` to a match list. No translation occurs. O(N).

**Pass 2 — selective top-down translation (pre-order with early exit):**
Walk the same expression tree top-down. At each node:

1. Is this node in the match list? → emit `"$_id.<subKey>"`,
   **stop recursion** (do not descend into children).
2. Otherwise → translate this node normally and recurse into children.

Stopping recursion on a match is the key mechanism. For
`SELECT x + 1 GROUP BY x, x + 1` where both `x` and `x + 1` are group
keys, both appear in the match list after Pass 1. In Pass 2:

- Reach `x + 1` first (top-down) → match → emit `"$_id.k1"`, stop.
  `x` is never visited. Canonicalization preserved. ✓

For sibling matches (`x` in left branch, `y` in right branch):

- Reach `x` → match → emit `"$_id.x"`, stop.
- Reach `y` → match → emit `"$_id.y"`, stop. ✓

**Overlapping matches are safe without explicit conflict detection.**
If `x` and `x + 1` both appear in the match list and Pass 2 replaces
`x + 1` first (top-down ordering guarantees this for a parent-child pair),
the `x` entry in the match list is never reached because descent stopped.
If the traversal order were reversed (bottom-up), replacing `x` would
discard the subtree containing it; the replacement would apply to a node
no longer referenced in the output tree — effectively a no-op. Either
way, no explicit overlap detection is needed: process all entries in the
match list; safe by construction.

**Total cost:** O(N) for Pass 1 + O(N) for Pass 2 = O(N). No O(N²)
structural comparison at any point.

### Leaf semantic identity

The leaf VN must reflect **semantic identity**, not textual name.
`a.city` and `b.city` are different columns that happen to share the
column name `city`. Using `ColumnKey("city")` for both would incorrectly
equate them.

For Hibernate's SQL AST, the correct identity is the resolved field path
including the table binding qualifier (e.g.
`ColumnKey("tableAlias.city")` or using the `ColumnReference`'s
`getQualifyingTableReference()` identity). This ensures two `x` references
to different tables produce different VNs even if the column name is the
same.

### Structural matching aligns with PostgreSQL semantics

Value numbering is purely structural: two expressions get the same VN
only if they are byte-for-byte identical after leaf-identity resolution.
Commutativity, associativity, and identity-element folding are not
recognized.

This mirrors PostgreSQL's GROUP BY semantics exactly. Verified against
Postgres 16 (Docker `postgres:latest`, port 5432) on 2026-08-18:

| Query | Result |
|-------|--------|
| `SELECT x + 1 FROM t GROUP BY x + 1` | ✅ works |
| `SELECT x + 1 FROM t GROUP BY 1 + x` | ❌ `column "t.x" must appear in the GROUP BY clause` |
| `SELECT 1 + x FROM t GROUP BY x + 1` | ❌ same error |
| `SELECT x + (y + z) FROM t GROUP BY (x + y) + z` | ❌ same error |
| `SELECT x FROM t GROUP BY x + 0` | ❌ same error |

Postgres does not fold commutative, associative, or identity-element
equivalences during GROUP BY-membership checking — it uses structural
equality. Our VN-based approach therefore matches Postgres behavior
without any canonicalization pre-pass. Users writing `GROUP BY 1 + x`
and `SELECT x + 1` will see the same "column not in GROUP BY" error
they would in Postgres.

If a future SQL dialect adds semantic canonicalization to GROUP BY, the
translator could add pre-VN normalization rules (sort commutative
operands, left-associate binary ops, drop `+ 0` / `* 1`, etc.) without
changing the framework.

### Result-set equivalence with PostgreSQL (verified 2026-08-18)

The three canonical shapes covered by the expression-key implementation
were run against PostgreSQL 16 (`postgres:latest` container on
`localhost:5432`) with seed `(1), (1), (2), (2), (3)` and produced:

| HQL | Postgres result set |
|-----|---------------------|
| `SELECT x + 1 FROM t GROUP BY x + 1` | `[2, 3, 4]` |
| `SELECT x + 1 FROM t GROUP BY x`     | `[2, 3, 4]` |
| `SELECT x + 1, x FROM t GROUP BY x, x + 1` | `[(2, 1), (3, 2), (4, 3)]` |

Our translator produces the same result sets against MongoDB, verified
by the corresponding integration tests
(`GroupByHavingIntegrationTests.ExpressionKeys`):

- `testWholeMatch` — whole-match substitution to `_id.k0`.
- `testLeafRewriteOverColumnKey` — leaf rewrite to `_id.primitiveInt`
  inside a composite `$project` expression.
- `testParentWinsOverLeafCanonicalization` — canonicalization: parent
  wholesale match wins over child leaf match, producing `_id.k1` rather
  than `{$add: ["$_id.primitiveInt", 1]}`.

## Formal foundations and academic references

The design draws on three well-established techniques from compiler
theory: **structural value numbering** for expression identity,
**strategic term rewriting** for the substitution walker, and
**hash-consing** for the interning table backing the VN registry.
This section maps each implementation choice to its formal literature.

### Structural value numbering (SVN)

Value numbering assigns a canonical integer identifier (the *value
number*, VN) to each distinct expression such that two expressions
receive the same VN if and only if they are structurally identical
under a chosen leaf-identity relation. The technique originates with
Cocke's global common-subexpression elimination work [Cocke1970] and
was formalized as *value numbering* by Ershov [Ershov1958].

Modern algorithmic treatments trace to:

- **Alpern, Wegman, Zadeck (1988)** [AWZ1988] — introduced the term
  "value numbering" in its contemporary form, defining a hash-based
  canonicalization procedure over a fixed set of operator symbols.
- **Rosen, Wegman, Zadeck (1988)** [RWZ1988] — established the SSA-plus-
  value-numbering framework that underpins LLVM's `GVN` pass.
- **Simpson (1996)** [Simpson1996] — Rice University PhD thesis
  formalizing the value-driven redundancy-elimination algorithms that
  LLVM's `NewGVN` implements.
- **Muchnick (1997)** [Muchnick1997], *Advanced Compiler Design and
  Implementation*, Chapter 12 — the canonical textbook treatment.

Our implementation is a **local, tree-form value numbering** (no SSA):
`VNRegistry.intern(tag, fields...)` uses `HashMap.computeIfAbsent` to
assign each fresh structural key an auto-incremented integer.
Composite keys `(opcode, leftVN, rightVN, ...)` are formed post-order
so the recursive step is O(1) after child VNs are known — the same
recurrence LLVM uses internally in
`ValueNumbering::createExpr(Instruction *I)` where operand value
numbers are looked up and combined into an expression key
[LLVMSource].

### Hash-consing and structural sharing

The `VNRegistry` interning table is a specialization of **hash-consing**
[Ershov1958, Goto1974, FilliatreConchon2006]. Hash-consing enforces
that structurally equal terms are represented by the same object
(pointer equality equals structural equality). We use the same
mechanism, but store the canonical integer rather than the object —
records give us the equal-hash-code invariant for free.

Java-record `equals`/`hashCode` are trivially structural [JLS-Records],
so `ExprKey(String tag, List<Object> fields)` is a valid hash-consing
key without additional machinery.

### Attribute grammars for VN memoization

Per-node `valueNumber(VNRegistry)` is a **synthesized attribute** in
the attribute-grammar sense [Knuth1968]: the value of the attribute at
a node is a function of the same attribute computed at its children.
`VNRegistry.memoize(node, compute)` provides the standard
memoization-of-synthesized-attributes technique using an
`IdentityHashMap<AstExpression, Integer>`. This gives each node an
amortized O(1) `valueNumber()` computation regardless of how many
times the method is invoked over the query's lifetime.

The choice of `IdentityHashMap` (reference equality) rather than
structural equality is intentional: two structurally-identical
`AstExpression` records produce the same VN via the ExprKey interning
path, and per-instance memoization additionally prevents redundant
subtree walks when the same instance is queried from multiple
enclosing rewrites (see `AstRewriter.rewrite(AstExpression)`).

### Strategic term rewriting

The two-phase `AstRewriter` (pre-rules top-down, post-rules bottom-up,
one-visit-per-node with descent short-circuit on match) implements a
subset of the **strategic term rewriting** framework introduced by
Visser [Visser2001] in the Stratego language. Stratego's core
combinators — `topdown`, `bottomup`, `try`, `choice`, `all` — express
tree traversals as compositions of primitive rewrites. Our simpler
model fixes the traversal (single top-down descent with post-order
finalization) but is drawn directly from the same conceptual space.

Related term-rewriting frameworks in the same family:

- **TXL** [CordyMalton1998] — the earliest tree-transformation
  language with strategy combinators.
- **Rascal** [KlintVanDerStormVinju2009] — a modern descendant with a
  larger domain-specific-language ambit.
- **ELAN** [BorovanskyKirchnerKirchnerRingeissen1996] — the theoretical
  precursor that formalized strategy calculi.

Our `RewriteRule<AstNode>` corresponds to a primitive rewrite; the
list-of-rules `AstRewriter` constructor argument corresponds to
Stratego's `choice(rules)` combinator (first match wins for pre-rules)
and `seq(rules)` combinator (each post-rule sees the previous rule's
output).

### Semantic alignment with PostgreSQL

Value numbering is **structural** — the equivalence relation is
"same tree shape modulo leaf identity" — and does not fold
commutative, associative, or identity-element equivalences. This
matches PostgreSQL's `GROUP BY`-membership check exactly (verified
above): Postgres does not accept `SELECT x + 1 GROUP BY 1 + x` even
though the expressions are semantically equal. SQL standard SQL:2016
§7.9 permits implementations to check functional determination
structurally; both Postgres and our implementation take that option.

Semantic canonicalization (via rewrite normalization rules pre-VN)
would relax our behavior beyond Postgres. The framework is prepared
for it — pre-normalization can be added as ordinary `RewriteRule`s
running before VN population — but the current design deliberately
does not add it, to preserve Postgres-equivalent semantics.

### Accumulator dedup via VN

The accumulator infrastructure reuses VN as a structural-identity
device. When the SELECT/HAVING visitor encounters an aggregate
function call `SUM(x + 1)`, we translate its argument to
`AstExpression`, wrap in `AstUnaryOperatorExpression("$sum", arg)`,
compute VN, and `computeIfAbsent` in a `LinkedHashMap<Integer,
AstElement>`. Same VN → same accumulator entry; the LinkedHashMap's
insertion-ordered `values()` view is emitted in `$group` alongside
`_id`.

This is a direct application of **common-subexpression elimination**
[Cocke1970] to accumulator identification. The classical CSE step
inside a compiler eliminates redundant computation of the same
value; we use the same VN-based test to eliminate redundant
accumulator entries. Because SQL aggregate arguments are
per-row-scope (evaluated on raw input rows before grouping), the
accumulator's *inner* expression is not subject to GROUP BY
substitution — we register it raw, and the walker never rewrites
inside `$group`.

The ordering constraint (HAVING scanned before SELECT so
HAVING-only accumulators land in the map in time for the `$group`
emit) is the same phase-order that Rosen-Wegman-Zadeck [RWZ1988]
identify for global value numbering with side-effecting operators.

### Summary of implementation-to-theory mapping

| Implementation artifact | Formal concept | Reference |
|-------------------------|----------------|-----------|
| `AstExpression.valueNumber()` | Synthesized attribute | Knuth 1968 |
| `VNRegistry.memoize(node, ...)` | Attribute memoization | Standard AG technique |
| `VNRegistry.intern(tag, fields...)` | Hash-consing | Ershov 1958, Goto 1974 |
| `ExprKey(tag, List<Object>)` structural identity | Structural equality | Java records / JLS |
| Composite VN from child VNs | Local value numbering | Alpern-Wegman-Zadeck 1988 |
| `AstRewriter` pre-rule top-down | `topdown` strategy | Visser 2001 (Stratego) |
| `AstRewriter` post-rule bottom-up | `bottomup` strategy | Visser 2001 (Stratego) |
| Rule short-circuit on match | `choice` combinator | Visser 2001 |
| `GroupBySubstitutionRule` | Structural substitution | Term rewriting theory |
| Accumulator `LinkedHashMap<VN, ...>` dedup | Common subexpression elimination | Cocke 1970 |
| Per-instance `IdentityHashMap` cache | Reference-equality memoization | Standard CS technique |

### Bibliography

- **[Cocke1970]** Cocke, J. (1970). "Global common subexpression
  elimination". *Proceedings of a symposium on Compiler optimization*,
  20–24.
- **[Ershov1958]** Ershov, A.P. (1958). "On programming of arithmetic
  operations". *Communications of the ACM* 1(8): 3–6.
- **[Goto1974]** Goto, E. (1974). "Monocopy and associative
  algorithms in extended Lisp". Technical Report TR-74-03,
  University of Tokyo.
- **[Knuth1968]** Knuth, D.E. (1968). "Semantics of context-free
  languages". *Mathematical Systems Theory* 2(2): 127–145.
- **[AWZ1988]** Alpern, B., Wegman, M.N., Zadeck, F.K. (1988).
  "Detecting equality of variables in programs". *POPL '88*: 1–11.
- **[RWZ1988]** Rosen, B.K., Wegman, M.N., Zadeck, F.K. (1988).
  "Global value numbers and redundant computations". *POPL '88*:
  12–27.
- **[CytronFerranteRosenWegmanZadeck1991]** Cytron, R., Ferrante, J.,
  Rosen, B.K., Wegman, M.N., Zadeck, F.K. (1991). "Efficiently
  computing static single assignment form and the control dependence
  graph". *TOPLAS* 13(4): 451–490.
- **[Simpson1996]** Simpson, L.T. (1996). *Value-Driven Redundancy
  Elimination*. PhD thesis, Rice University.
- **[Muchnick1997]** Muchnick, S.S. (1997). *Advanced Compiler
  Design and Implementation*. Morgan Kaufmann. Ch. 12.
- **[CordyMalton1998]** Cordy, J.R., Malton, A.J. (1998). "TXL: A
  language for programming language tools and applications". *Proc.
  8th Intl. Conf. on Compiler Construction*.
- **[BorovanskyKirchnerKirchnerRingeissen1996]** Borovanský, P.,
  Kirchner, C., Kirchner, H., Ringeissen, C. (1996). "Rewriting with
  strategies in ELAN: A functional semantics". *Intl. Journal of
  Foundations of Computer Science*.
- **[Visser2001]** Visser, E. (2001). "Stratego: A language for
  program transformation based on rewriting strategies. System
  description of Stratego 0.5". *RTA '01*: 357–361.
- **[FilliatreConchon2006]** Filliâtre, J-C., Conchon, S. (2006).
  "Type-safe modular hash-consing". *ML Workshop*: 12–19.
- **[KlintVanDerStormVinju2009]** Klint, P., van der Storm, T.,
  Vinju, J. (2009). "RASCAL: A domain specific language for source
  code analysis and manipulation". *SCAM '09*.
- **[LLVMSource]** LLVM Project. `llvm/lib/Transforms/Scalar/GVN.cpp`
  and `NewGVN.cpp` — production reference implementations.
- **[JLS-Records]** Java Language Specification §8.10 (records) — the
  auto-generated `equals`/`hashCode` contract mandates structural
  equality over the record's components.

## Open questions

- **Sub-key name collisions between column keys (`address#city`) and
  positional keys (`k0`)**: append `_<i>` on collision. Vanishingly rare.
- **Nested subqueries**: out of scope — already throws at
  `visitSelectStatement` line 505.

## Follow-up tickets

- **Sub-ticket A**: `AstExpression.children()` scaffolding (if we ever need
  a generic tree walker — not strictly required for Variant B).
- **Sub-ticket B**: expression-key support (this document).
- **Sub-ticket C** (HIBERNATE-196 dependency): function keys once
  `visitSelfRenderingExpression` yields into EXPRESSION mode.

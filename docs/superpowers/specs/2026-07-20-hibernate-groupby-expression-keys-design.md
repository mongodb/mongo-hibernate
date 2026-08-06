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

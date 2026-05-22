---
name: add-hql-to-mql-translation
description: Use when designing or planning support for a new HQL construct in the MongoDB MQL translator (AbstractMqlTranslator). Covers domain-specific investigation steps and conventions that augment the standard brainstorming and writing-plans superpowers skills.
---

# Adding HQL-to-MQL Translation Support

This guidance covers domain-specific investigation steps and conventions for adding support for a new HQL construct in `AbstractMqlTranslator`. It augments the standard brainstorming → writing-plans → subagent-driven-development workflow; it does not replace it.

---

## Phase 1: Investigate Before Brainstorming

Do this before brainstorming.

### 1a. Nail the MongoDB mechanism

- What aggregation stage(s) implement this construct? What are all the supported forms?
- Which shapes map to a simple stage form vs. a complex form requiring `$expr` or nested pipelines?
- Write 2–3 concrete pipeline examples covering the key shapes. These become the spec's "Concrete Examples" section.
- If translation is not obvious, verify the mechanism by creating test data and executing pipelines in `mongosh`

### 1b. Investigate the Hibernate SQL AST

Extract source from the Hibernate sources JAR at `~/.gradle/caches/.../hibernate-core-<version>-sources.jar` (version from `gradle/libs.versions.toml`).

**Read `AbstractSqlAstTranslator`** — find the method(s) that handle this construct. Every branch the SQL translator handles is a shape you must either translate or explicitly throw for.

**Read the SQL AST node class(es)** for the construct to understand what fields/methods are available.

**Known gotchas** — patterns that are easy to miss when translating Hibernate's SQL AST:

- **`isVirtual()` table groups** — virtual table groups are embeddable wrappers that don't render to a JOIN themselves; the SQL translator descends into their sub-joins without emitting a JOIN clause. Do the same: skip emitting a stage but recurse into sub-joins.

**For any new unsupported shape:** confirm it is reachable from HQL (not just handcrafted SQL AST) so negative tests can be written through HQL. Check `SemanticQueryBuilder` and the HQL grammar in `HqlParser.java` to verify the syntax.

### 1c. Enumerate all shapes

Build a complete table: every combination of the construct that's syntactically valid in HQL. Mark each ✅ (translatable) or ❌ (throw). For ❌ shapes, identify which ticket will track implementation.

**File Jira tickets for every ❌ shape before writing any code.** Use real ticket numbers in throw messages from day one — no literal `HIBERNATE-NNN` placeholders where `NNN` hasn't been replaced with an actual number. File the Jira tickets first, then reference them in the code. The codebase convention uses a `TODO-` prefix on these strings to signal the feature is not yet implemented; follow that convention for new throws (e.g. `"TODO-HIBERNATE-161 https://jira.mongodb.org/browse/HIBERNATE-161"`). File against project HIBERNATE at https://jira.mongodb.org/browse/HIBERNATE using the `jira` CLI.

---

## Phase 2: Brainstorm and Spec

The spec should follow this structure:

- **MongoDB Mechanism** — concrete pipeline examples for each supported shape
- **Pipeline Structure** — where new stages appear relative to `$match` → `$sort` → `$skip/$limit` → `$project`
- **Implementation Approach** — new AST node classes (if any), exact changes to `AbstractMqlTranslator`
- **Supported and Unsupported Shapes** — table with Jira ticket for each ❌
- **Tests** — table listing each positive and negative test with what it covers

---

## Phase 3: Write the Plan

The plan must follow this task order:

1. **New AST node(s)** — unit test (`assertRendering`) then implementation, one task per record class; TDD
2. **Integration test skeleton** — create test class with entities, seed data, ONE positive test, ONE negative test (both failing); commit
3. **Core translator changes** — implement; run first positive test; run existing integration tests for regressions; commit
4. **Remaining positive tests** — all positive cases; run full positive suite; commit
5. **Coverage verification** *(do not skip)* — add temporary `System.err.println` markers to all non-trivial code paths (loops, recursion, branches); run targeted tests; confirm each path is actually exercised; remove logging; re-run suite. Report and investigate if any path is uncovered before proceeding.
6. **Negative tests** — all `FeatureNotSupportedException` cases; run full suite; commit
7. **Final build** — `./gradlew build` + full integration test run

**Test conventions:**

- Positive tests: `assertSelectionQuery(hql, resultType, expectedMql, expectedResults, expectedAffectedCollections)` — always assert the full MQL pipeline string, the full result set, and the full set of affected collections
- When the `$project` fields are unknown upfront (e.g. JOIN FETCH): use `assertActualCommandsInOrder` with `/* FILL IN after first test run */`; upgrade to full `assertSelectionQuery` once the actual MQL is known
- Negative tests: `assertSelectQueryFailure(hql, resultType, FeatureNotSupportedException.class, "TODO-HIBERNATE-161 ...")` — use the real ticket number, following the `TODO-HIBERNATE-NNN` codebase convention
- Positive tests live in `@Nested class Positive` with `@BeforeEach` seed data; negative tests live at the outer class level (no seed data needed)
- Every non-trivial code path (loop iterations, recursive calls, each instanceof branch) must be covered by a positive or negative test

---

## Phase 4: Implement

Follow the subagent-driven-development workflow: fresh subagent per task, spec compliance review then code quality review after each task.

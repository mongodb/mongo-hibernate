---
name: reviewing-pull-requests
description: Use when reviewing a pull request, branch, or commit in this repository --- HQL/MQL translation, dialect, JDBC adapter, type system, or schema/DDL changes.
---

# Reviewing Pull Requests

## Core principle

**A claim about runtime behavior you did not execute is a hypothesis, not a finding.**

This project translates one AST into another and dispatches on runtime value-descriptor matching.
Whether a construct produces correct MQL, throws a clean `FeatureNotSupportedException`, or trips an
internal assertion is decided by paths that reading a diff cannot settle. A review that stops at
reading yields "this gate looks fragile"; a review that runs the code yields "this gate is already
broken, here is the HQL that crashes it, and here is the parent commit behaving correctly".

## The gate

Every correctness claim in the review is labeled:

- **Verified** --- you executed something and observed the result. Quote the input and the output.
- **Unverified** --- reasoning only. Say so, in the finding itself.

An unlabelled correctness claim is not ready to deliver. Design, naming, and dead-code findings need
no execution; anything about *behavior* does.

## Phase 1 --- Ground truth, not local inference

- `gh pr checkout <n>` --- get the branch. Use this, not `git fetch origin <branch>`: PRs routinely
  come from contributor forks, where that fails with `couldn't find remote ref`.
- `gh pr view <n> --json title,body,files,commits` --- the author's own statement of scope. Read the
  commit subjects too: a `DO-NOT-MERGE` or `fixup` commit that *narrows a test* is a finding in itself.
- `jira issue view HIBERNATE-<n> --plain` --- real ticket scope and status for the PR's ticket *and*
  for every ticket cited in a `TODO-HIBERNATE-NNN` throw the PR adds, keeps, or deletes. A throw that
  now fires for a different reason than its ticket describes is a finding. Read the **comments**: an
  unresolved "needs product input" question is worth surfacing before any code discussion.

**Route by area.** For HQL-to-MQL translation PRs, the review checklist is the companion skill's
standard: **REQUIRED:** use `add-hql-to-mql-translation`. Its Phase 1c shape table and its Phase 3
step 5 coverage verification are what a reviewer checks the PR against --- hold the PR to that
standard whether or not the author used the skill.

## Phase 2 --- Establish a green baseline first

```bash
./gradlew build -x integrationTest
./gradlew :integrationTest
```

Both must pass *before* you probe. Otherwise a failure you hit later is unattributable. Report the
baseline in the review so readers know your findings came from untested shapes, not a broken branch.

Integration tests need the local replica set (see AGENTS.md); confirm with
`mongosh --quiet --eval 'db.hello().ok'`.

## Phase 3 --- Probe, and diff against the parent commit

Probing is never optional. The **parent-commit** half is what separates *pre-existing limitation*
from *regression this PR introduced* --- the difference between a nitpick and a blocker.

Pick the template that matches the PR:

| PR touches | Template | Copy to |
|---|---|---|
| HQL / translation / query behaviour | `ProbeTests.java.template` | `src/integrationTest/java/com/mongodb/hibernate/query/ZzProbeTests.java` |
| Schema export, DDL, indexes, JDBC admin commands | `DdlProbeTests.java.template` | `src/integrationTest/java/com/mongodb/hibernate/query/ZzDdlProbeTests.java` |

Run it on the PR. **The leading colon is required** --- without it Gradle runs the task in the Spring
Boot subprojects too and fails with `No tests found for given includes`.

```bash
./gradlew :integrationTest --tests ZzProbeTests
grep -ohE 'PROBE\|[^<]*' build/test-results/integrationTest/TEST-*ZzProbeTests.xml
```

Then run the **same** probe file on the parent commit and diff the two outputs:

```bash
BASE=$(mktemp -d)/base
git worktree add "$BASE" <parent-sha>
mkdir -p "$BASE/src/integrationTest/java/com/mongodb/hibernate/query"
cp src/integrationTest/java/com/mongodb/hibernate/query/ZzProbeTests.java \
   "$BASE/src/integrationTest/java/com/mongodb/hibernate/query/"
"$BASE/gradlew" -p "$BASE" :integrationTest --tests ZzProbeTests
```

Present the comparison as a table: shape | parent | PR | verdict. A cell going from a clean throw to
`AssertionError` is a regression; from a clean throw to correct MQL is the intended feature.

**How much parent run is warranted.** If the parent already implements the feature area, probe every
shape on both sides --- that is where regressions hide. If the parent throws unconditionally for the
whole area (the PR adds a wholly new capability, so there is no prior behavior to regress from), run
**one** probe on the parent to confirm that unconditional throw, then spend the remaining effort on
mapping-fidelity and lifecycle shapes on the PR side instead. Confirm the predicate; don't assume it.

## What to probe --- the context matrices

PRs habitually test one context and leave the rest working-but-unverified or silently broken. Each
row below is a *different code path*, not a variation of the same one.

### SELECT / WHERE parity --- check this on every translation PR

`ARCHITECTURE.md` requires a construct to work in both clause positions where feasible, and specifies
which `$match` form is correct for which operand shape. What a reviewer adds is the evidence:

- **Probe both positions.** A `SELECT`-only implementation is incomplete, not done. If a position is
  out of scope, look for the negative test and the ticket; silence is the finding.
- **Read which form was emitted**, not just whether it worked. `$expr` where the compact field-vs-value
  form was available is a finding; find syntax where the semantics need `$expr` is a correctness bug.
- Probe a field-vs-value case and a computed-operand case **side by side** and compare the two
  pipelines --- the query template ships with exactly that pair for this reason.

### Query and translation contexts

| Context | Reach it with | Emits |
|---|---|---|
| SELECT projection | `select f(x) from E` | `$project` |
| WHERE, computed operand | `where f(x) = v` | `$expr` inside `$match` --- the normal case |
| WHERE, field vs. value | `where t.n = v` | compact find syntax, `{"n": {"$eq": v}}` |
| Predicate operand | `where f(x) like '...'`, `in`, `is null` | expects `FIELD_PATH`, not `EXPRESSION` |
| ORDER BY / GROUP BY / HAVING | `order by f(x)` | often a tracked throw --- confirm which |
| Mutation | `update E set a = f(x)`, `delete ... where f(x)` | update pipeline |
| Composition | `f(g(x))`, `f(x) = f(y)`, `f(x) = column` | recursion |
| Absent / null values | nullable column, missing field | server-side rejection |
| Literal vs parameter | `f(x, 2)` vs `f(x, :p)` | constant folding, `$literal` wrapping |

### Schema, DDL, and JDBC contexts

Schema export runs at `SessionFactory`-build time, not per query, so it needs its own harness
(`DdlProbeTests.java.template`). Assert the **actual command sent to the server** --- "the server
accepted it" and "the server built what the mapping asked for" are different claims, and a mapping
detail that is silently dropped throws nothing at all.

| Context | Reach it with | Assert |
|---|---|---|
| Each export action | `schema-generation.database.action` = `create`, `drop-and-create`, `create-drop`, `drop` | which admin commands actually fire for each |
| Lifecycle | `create-drop`, then close the `SessionFactory` | the drop command is really emitted, not just implemented |
| Idempotency | run the same export twice | second run's commands and any error |
| Mapping fidelity | `columnList = "c DESC"`, compound, nested paths, unique vs not | the emitted key document matches the mapping exactly |
| Competing idioms | `@Column(unique = true)` vs `@Table(uniqueConstraints = ...)` | that Hibernate materializes the constraint at all --- inspect boot metadata, not just the command |
| Naming | `@Table(name = , schema = )` | collection name in the command, including any prefix |

**Both templates register their own `CommandListener`** through the public
`MongoConfigurationContributor` SPI rather than borrowing the integration-test framework's, whose shape
and lifetime change over time. Owning the listener keeps probes stable across that churn.

Two operational rules:

- **Run one probe class at a time.** The suite runs test classes concurrently, and `System.out` from
  two concurrent classes is interleaved and misattributed between XML report files.
- **Always read the capture count.** `(none of 0 captured)` means the listener was never installed ---
  a broken harness telling you nothing. Usual cause: Hibernate's `ServiceRegistry` holds one instance
  per service interface and the last registration wins, and auto-discovered `ServiceContributor`s are
  applied inside `build()`, after an explicit `addService` --- so a contributor on the test classpath
  replaces the probe's and only its listener is installed. `(none of 3 captured)` is the opposite:
  capture worked and no interesting command was emitted, which is itself often the finding.

**Widened-set heuristic.** When a change gates behaviour on an implicit set --- a package name, an
`instanceof`, a registry, a annotation scan --- enumerate that set's *current* members and probe each
one. The set includes classes the author never considered, and the compiler will not tell you.

## Architecture conformance

`ARCHITECTURE.md` states the binding rules --- translation goes through the visitor, state is carried
in translator fields, which filter language belongs in `$match`, the failure contract. Read it before reviewing a
translation PR. These are the diff symptoms that indicate a violation:

- A new class that takes a `SqlAstNode` and inspects its type directly instead of calling
  `acceptAndYield(node, DESCRIPTOR)` and letting dispatch happen.
- A `render`-style method that recurses into child nodes itself rather than yielding each child.
- A translation that works with **no existing `visitXxx` modified**, for a construct whose operands
  can be arbitrary expressions. Correct output today is not a defence: a parallel descent cannot see
  translator state and will diverge on the next change.
- A new field on the translator, sitting alongside `elemMatchInnerAlias` and `letVariableCounter` ---
  that is the *sanctioned* pattern, so it is a point in the PR's favour, not a smell.

## Reading outcomes

`ARCHITECTURE.md` defines what the exception types mean. This is what to *do* on seeing each:

| Observed | Reviewer action |
|---|---|
| `FeatureNotSupportedException` with `TODO-HIBERNATE-NNN` | Open the ticket. Confirm it actually covers the shape that threw, and is still open. |
| `FeatureNotSupportedException` with a bare message | Not automatically a finding. Ask whether the limitation is inherent to MQL --- then no ticket is wanted --- or something we mean to implement, in which case it is. |
| `AssertionError` | Always a blocker: an internal invariant was violated. Report the exact HQL that reaches it. |
| `JDBCException` from the server | The translator emitted MQL the server rejects. Look for a missing argument-type or nullability guard. |
| Wrong MQL, correct result | Silent correctness risk. Assert the pipeline, not just the rows. |
| Command sent, but a mapping detail is missing from it | Silently dropped mapping (e.g. `DESC` flattened to `1`). Worst category: no error, wrong database. |
| No command at all, no error | The feature never engaged. Check whether Hibernate even materializes the metadata, before blaming the dialect. |

## Review output

Structure the review as: **Blockers** (verified correctness defects), then **Significant** (API and
semantics decisions worth settling pre-merge), then **Code quality**. For each finding give the file
and line, the evidence label, and --- for blockers --- the concrete input that fails. Close with what
gates merge versus what can be a follow-up.

For a blocker, characterize the failure far enough to name the mechanism --- which invariant broke,
which branch emitted the wrong form --- and reach for `superpowers:systematic-debugging` when the cause
is not obvious from the probe output. Then stop at the diagnosis: the fix belongs to the author, and a
review that arrives with a patch instead of a mechanism is harder to act on, not easier.

## Rationalizations

| Thought | Reality |
|---|---|
| "I read the diff carefully, that's a thorough review" | Reading found "fragile"; running found "already broken". Different outputs. |
| "The full suite passes, so it works" | The suite tests what the author thought to test. Probe what they didn't. |
| "It's obviously a regression, no need to run the parent" | Without the parent run you cannot tell regression from pre-existing limitation, and that distinction decides whether it blocks merge. |
| "Writing a probe test is too much work for a review" | The template is beside this file. The run takes seconds once Gradle is warm. |
| "This is a design PR, nothing to execute" | Then say Unverified. Don't dress reasoning up as a finding. |
| "The author already says this part is untested, so I'll just repeat that" | Verify it. "Untested" and "unreachable dead code" are different findings with different remedies, and only a probe tells them apart. |
| "The parent has no prior behaviour here, so probing is pointless" | The parent run is what *establishes* that. Confirm it with one probe, then redirect effort to mapping fidelity and lifecycle on the PR side. |
| "The author didn't use the translation skill, so it doesn't apply" | The skill is the project's standard for the construct, not a personal workflow. Review against it regardless. |
| "It works in SELECT, WHERE can be a follow-up" | Then it needs a negative test and a ticket. Untested-but-reachable is the failure mode this repo keeps hitting. |
| "The MQL is correct, so the parallel descent is fine" | It is correct *today*, for the shapes tried. It cannot see translator state and will diverge from the visitor on the next change. |

## Red flags --- stop

- About to write "this looks like it could break" without having tried to break it.
- About to call something a regression without a parent-commit run.
- Probe catches `Exception` instead of `Throwable` --- it will silently miss every `AssertionError`.
- Probe reports `(none of 0 captured)` --- the listener is not installed, so you are reading nothing
  and concluding something. Fix the harness before trusting a single result.
- Signed off on a translation without seeing its `$match` output next to its `$project` output.
- Probe file, worktree, or a temporarily-suppressed framework file still present at the end.

## Cleanup

Delete the probe files, remove the worktree, drop any collections the probes created, and confirm the
tree is clean before delivering:

```bash
rm -f src/integrationTest/java/com/mongodb/hibernate/query/ZzProbeTests.java \
      src/integrationTest/java/com/mongodb/hibernate/query/ZzDdlProbeTests.java
git worktree remove --force "$BASE"
mongosh --quiet mongo-hibernate-test --eval \
  'db.getCollectionNames().filter(n => n.startsWith("probe_")).forEach(n => db[n].drop())'
git status --short && git worktree list
```

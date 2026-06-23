# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Gemini CLI, etc.) when working with code in this repository.

## What This Project Is

MongoDB Extension for Hibernate ORM — a Hibernate dialect and JDBC adapter that lets applications use MongoDB as their database. Translates Hibernate's SQL AST into MongoDB aggregation pipelines.

## Build and Test Commands

```bash
./gradlew build                                        # full build including unit tests and checks
./gradlew test                                         # unit tests only
./gradlew integrationTest                              # integration tests (requires MongoDB, see below)
./gradlew test --tests ClassName                       # single test class
./gradlew integrationTest --tests ClassName            # single integration test class
./gradlew spotlessApply                                # auto-format code
./gradlew spotlessCheck                                # check formatting without applying
```

Integration tests require MongoDB replica set running at `localhost:27017` with `--enableTestCommands=1`. Override the connection string via `jakarta.persistence.jdbc.url` in `src/integrationTest/resources/hibernate.properties`. 
The replica set has typically already been started by the developer and is available.

## Architecture

### Layers

**`dialect/MongoDialect`** — Entry point. Registers type mappings, custom functions, and supplies the query translator to Hibernate.

**JDBC adapter** (`jdbc/MongoConnection`, `MongoStatement`, `MongoResultSet`) — Implements JDBC interfaces over the MongoDB Java Driver. Manages `ClientSession` lifecycle; `MongoResultSet` maps BSON documents to JDBC rows.

**Translators** (`internal/translate/`) — Convert Hibernate's SQL AST to MQL:
- `SelectMqlTranslator` — visitor pattern, emits an aggregation pipeline (`$match` → `$sort` → `$skip`/`$limit` → `$project`)
- `MutationMqlTranslator` — INSERT, UPDATE, DELETE

**Type system** — `MongoStructJdbcType` (embeddables / `@Struct`), `MongoArrayJdbcType` (collections), `ObjectIdJavaType`/`ObjectIdJdbcType` (ObjectId support).

### MongoDB AST

`internal/translate/mongoast/` contains the project's own AST for MongoDB pipeline stages, filters, and expressions. The translators build this AST, which is then serialized to BSON by the JDBC adapter.

### Module Boundaries

The Java module (`com.mongodb.hibernate`) exports only `cfg`, `cfg.spi`, and `annotations`. Everything under `internal/` is implementation-private.

## Jira

Project: **HIBERNATE** at https://jira.mongodb.org/browse/HIBERNATE

Check that the `jira` CLI is installed at `/opt/homebrew/bin/jira`. If not, ask the developer to install it:

```bash
brew install ankitpokhrel/jira-cli/jira-cli
```

The `jira` CLI handles basic issue operations (`jira issue view`, `jira issue edit`, `jira issue move`, etc.).

Retrieve the bearer token from the macOS Keychain:

```bash
TOKEN=$(security find-generic-password -s "jira-cli" -w)
```

If `jira` commands fail with a missing configuration error, ask the developer to run `jira init` (link: `https://jira.mongodb.org`, login: their MongoDB email).

If `TOKEN` is empty, ask the developer to create a Personal Access Token at https://jira.mongodb.org/tokens and add it to the keychain (`security add-generic-password -s "jira-cli" -a "their.name@mongodb.com" -w "<token>"`).
This assumes they are on a Mac.  If not, figure out the Linux or Windows equivalent.

For operations the CLI doesn't support natively (e.g. re-ordering/ranking issues within an epic), use `curl` with `$TOKEN` and the Jira Agile REST API, e.g.:

```bash
# Re-order: move HIBERNATE-XXX to rank before HIBERNATE-YYY
curl -s -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"issues":["HIBERNATE-XXX"],"rankBeforeIssue":"HIBERNATE-YYY"}' \
  "https://jira.mongodb.org/rest/agile/1.0/issue/rank"
```

## Adding HQL-to-MQL Translation Support

@.claude/skills/add-hql-to-mql-translation/SKILL.md

## Code Style

- **JDK:** Java 17 (matches CI). Verify with `java -version` before building. An `.sdkmanrc` is provided if the developer uses sdkman.
- **Formatter:** Spotless + Palantir Java Format. Always run `./gradlew spotlessApply` before committing.
- **Nullness:** JSpecify annotations enforced by NullAway (via Error Prone at compile time). Annotate all API boundaries.
- **Line length:** 120 characters.
- **Test class naming:** suffix `Tests`, not `Test`.
- **Integration test registry setup:** every integration test that builds a Hibernate SessionFactory/ServiceRegistry 
  through the testing framework must implement MongoServiceRegistryProducer (in com.mongodb.hibernate.junit). It strips the 
  SharedDriverManagerConnectionProvider that Hibernate's test framework injects, which would otherwise trip the 
  auto-configured connection provider.

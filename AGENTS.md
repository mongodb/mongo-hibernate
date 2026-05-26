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

The `jira` CLI (`/opt/homebrew/bin/jira`) is installed and pre-authenticated via a bearer token in `~/.config/.jira/.config.yml`. Use it for basic issue operations (`jira issue view`, `jira issue edit`, `jira issue move`, etc.).

For operations the CLI doesn't support natively (e.g. re-ordering/ranking issues within an epic), use `curl` with the bearer token and the Jira Agile REST API:

```bash
# Re-order: move HIBERNATE-XXX to rank before HIBERNATE-YYY
curl -s -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"issues":["HIBERNATE-XXX"],"rankBeforeIssue":"HIBERNATE-YYY"}' \
  "https://jira.mongodb.org/rest/agile/1.0/issue/rank"
```

To obtain `$TOKEN`, try these in order until one returns a non-empty value:

1. **Config file:** `grep authentication_token ~/.config/.jira/.config.yml | awk '{print $2}'`
2. **macOS Keychain:** `security find-generic-password -s "jira-cli" -w`
3. **Linux Secret Service:** `secret-tool lookup service jira-cli`

If none of these work, ask the user where their Jira API token is stored. If they don't have one, they can generate a Personal Access Token at https://jira.mongodb.org/secure/ViewProfile.jspa (Profile → Personal Access Tokens) and configure it with `jira init`.

The Jira REST API (`https://jira.mongodb.org/rest/api/2/`) is publicly readable without auth.

## Adding HQL-to-MQL Translation Support

@.claude/skills/add-hql-to-mql-translation/SKILL.md

## Code Style

- **JDK:** Java 17 (matches CI). Verify with `java -version` before building. An `.sdkmanrc` is provided if you use sdkman.
- **Formatter:** Spotless + Palantir Java Format. Always run `./gradlew spotlessApply` before committing.
- **Nullness:** JSpecify annotations enforced by NullAway (via Error Prone at compile time). Annotate all API boundaries.
- **Line length:** 120 characters.
- **Test class naming:** suffix `Tests`, not `Test`.

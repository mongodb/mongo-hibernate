# Schema and catalog qualification

This note covers how Hibernate's table qualifiers (`@Table(schema = …)`, `@Table(catalog = …)`,
and the global `hibernate.default_schema` / `hibernate.default_catalog` settings) map onto
MongoDB. The mapping: catalog → a MongoDB database, schema → a prefix on the collection name
within that database. Schema qualification is in scope for GA; catalog → database is deferred
until MongoDB supports cross-database `$lookup`, so catalog is rejected at boot. Until then, an
application that needs a second database uses a second SessionFactory.

Status: targeted for GA and implemented in the accompanying commit.

## Background: why there is a mismatch

The word "schema" is used two ways. One is the *shape* of the data (columns and types) — the
sense in which MongoDB is "schemaless." That is not what this note is about. The other is a
namespace: a `CREATE SCHEMA` container that holds tables. The two are related — a SQL schema is
a named collection of structure definitions — but distinct, and `@Table.schema` refers to the
namespace sense.

For namespaces, the SQL standard defines a three-level hierarchy — catalog → schema → table.
MongoDB has a two-level one — database → collection. The two levels are not equivalent in what
operations cross them:

- **Schema** is only naming. Cross-schema joins and cross-schema transactions always work; a
  schema is a namespace inside one catalog.
- **Catalog** is a physical database boundary. Cross-catalog joins and transactions are
  database-dependent and often unsupported (e.g. PostgreSQL binds a connection to one catalog
  and cannot cross it).

A MongoDB database is the analog of a SQL catalog: MySQL and SQL Server model a database as the
catalog level, and MongoDB has the same physical-database boundary. MongoDB has no equivalent
of the middle, schema level. Its collection names, however, can contain dots, and the dot is
conventionally used as a namespace separator — GridFS names a bucket's collections
`bucket.files` and `bucket.chunks`, and the server's internal collections are `system.*`.

One MongoDB property has no relational analog: a single deployment can run a transaction across
databases, but `$lookup` cannot join across them. A database boundary that behaves like a SQL
catalog — where cross-catalog joins and transactions both work — is therefore not yet
achievable.

## Behavior

**Schema folds into the collection name.** A schema-qualified entity maps to a collection named
`schema.name` (the dot is an ordinary character in a MongoDB collection name; the result is a
plain collection name). Every collection therefore lives in the one database the SessionFactory
is configured with. Relational schemas are namespaces you can join and transact across, and
keeping everything co-located preserves that: cross-schema joins and multi-collection
transactions work, and a schema-annotated application migrates without its joins breaking. The
global `hibernate.default_schema` setting is honored the same way, prefixing every
otherwise-unqualified collection.

**Catalog maps to a database, but is deferred.** A MongoDB database is the analog of
a SQL catalog (MySQL and SQL Server model a database as the catalog level), so the intended
mapping is catalog → database. That needs cross-catalog joins and transactions to behave as they
do relationally, but MongoDB has cross-database transactions and does not currently support
cross-database `$lookup`, so it cannot behave correctly yet: entities in different databases
could not be joined until the server adds that support. Folding catalog into the collection name
(the way schema is folded) is rejected as a stopgap that would force a breaking rename if and when
support lands in the server. Instead, catalog is rejected at boot — not silently dropped, as 
Hibernate would otherwise do — which fails loud per the extension's convention and reserves the 
mapping, so that catalog → database is an additive change when cross-database `$lookup` ships. 
To use more than one database, create a SessionFactory per database; that has neither 
cross-database joins nor cross-database transactions.

**Fold-induced name collisions are rejected.** Hibernate lets two entities map to the same table
and does not reject it, so it will not catch two *distinct* qualifiers that only collide after
folding (e.g. `@Table(schema = "a", name = "b")` and `@Table(name = "a.b")`, both `a.b`) — they
would silently co-mingle in one collection. Genuine table sharing (the same qualified name,
including a `SINGLE_TABLE` hierarchy's subclasses) is left untouched, because it is a mapping
Hibernate itself supports.

**Collection-name validity is left to the driver and server.** The extension does not
pre-validate a composed collection name beyond what the driver's `MongoNamespace` enforces
(non-null, non-empty — which Hibernate already ensures). A name the server rejects or treats
specially — a reserved `system.` prefix, a `$`, an over-length namespace — fails at first use, as
it would through any driver. The extension adds no name checks that drivers do not.

**Dot separator.** The dot matches both the SQL qualified-name form (`schema.table`) that
developers already know and MongoDB's own collection-name namespace convention (see Background),
so no escaping or alternate separator is needed.

## Notes

- Collection names are case-sensitive, so `schema = "S"` and `schema = "s"` are different
  collections.
- Inheritance: under `SINGLE_TABLE` — the only strategy currently supported — a hierarchy maps
  to one collection, so per-entity schema divergence cannot arise. When `JOINED` and
  `TABLE_PER_CLASS` land, differing schemas across a hierarchy fold into different collection
  names in the one database, and the inheritance key-joins remain same-database joins.

## Alternatives for catalog

The current behavior rejects any catalog at boot. Two alternatives to that were considered.

**Accept a catalog that equals the configured database name.** When `@Table(catalog = "…")` (or
`hibernate.default_catalog`) names the same database the connection string already points at, the
catalog is redundant — everything still lives in the one connected database, so joins and
transactions all work and no second database is introduced. This would let an application that
qualifies its tables with its single database name boot instead of being rejected, while a
catalog naming a *different* database stays rejected. It is a safe subset of the eventual
catalog → database support, with no join or routing implications. It requires the configured
database name to be available at boot.

**Support catalog → database now, documenting the join limitation.** Route each catalog to a
distinct MongoDB database immediately, accepting that a cross-catalog join fails until MongoDB
adds cross-database `$lookup`. This delivers real multi-database use — and cross-database
transactions work today — but the join failure surfaces at query time, including for joins the
ORM generates implicitly (lazy loads, `JOIN FETCH`).

## Supported and unsupported shapes

| Shape | Result | Status |
|---|---|---|
| `@Table(name = "t")` | collection `t` | ✅ |
| `@Table(name = "t1.t2")` | collection `t1.t2` (dot kept, name not split) | ✅ |
| `@Table(schema = "s", name = "t")` | collection `s.t`, same database | ✅ |
| `@Table(schema = "s", name = "t1.t2")` | collection `s.t1.t2`, same database | ✅ |
| `hibernate.default_schema = "s"` | unqualified entities prefixed with `s.` | ✅ |
| Query/join across schema-qualified entities | same-database `$lookup`, works | ✅ |
| Multi-collection transaction across schemas | same-database transaction, works | ✅ |
| Two entities on the same `@Table` (Hibernate table sharing) | shared collection | ✅ unchanged from Hibernate |
| `@Table(catalog = "c", …)` / `hibernate.default_catalog` | rejected at boot | ❌ deferred — intended as `catalog → database`, pending cross-database `$lookup` |
| Distinct qualified names that fold to one collection (e.g. `schema="a",name="b"` and `name="a.b"`) | rejected at boot | ❌ |
| `@SecondaryTable(schema = …)` | secondary tables not supported | ❌ HIBERNATE-181 |
| Per-entity schema under `JOINED` | deferred with JOINED inheritance | ❌ JOINED_TABLE |
| Per-entity schema under `TABLE_PER_CLASS` | deferred with TABLE_PER_CLASS inheritance | ❌ TABLE_PER_CLASS |

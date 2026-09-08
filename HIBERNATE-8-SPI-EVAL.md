# hibernate-8-spi-eval branch state

This branch migrates the MongoDB Extension for Hibernate ORM to the
Hibernate 8.0 SPI restructure in hibernate/hibernate-orm PR 13299
(branch `ast-translator-packages-80`, HHH-20747 and HHH-20748).
It is an evaluation branch, not production work.

## Build and test state

The extension compiles against `org.hibernate.orm:hibernate-platform:8.1.0-SNAPSHOT`
and all root-module tests pass: 450 unit tests and 871 integration tests.
The upstream snapshot must be published to local mavenLocal from a checkout
of the branch head (currently commit 45cf13ecc0 on PR 13302, versioned
8.1.0-SNAPSHOT):

    cd <hibernate-orm checkout>
    ./gradlew :hibernate-core:publishToMavenLocal :hibernate-testing:publishToMavenLocal \
        :hibernate-dialect-testkit:publishToMavenLocal :hibernate-platform:publishToMavenLocal \
        :hibernate-community-dialects:publishToMavenLocal -x :hibernate-community-dialects:javadoc

Publishing `hibernate-community-dialects` requires skipping javadoc because the
branch has three javadoc errors in that module. The 8.1 chain also depends on
the `jakarta.persistence-api` 4.0.0 snapshot, resolved from the Sonatype
snapshots repository (added to this branch's build files).

Two known limitations:

- The spring-boot autoconfigure module's integration tests fail under 8.0.
  Hibernate 8 brings Jakarta Persistence 4.0 while Spring Boot 4.0.7 and
  Spring Data are built against 3.2, so Spring Data repository calls fail with
  `NoSuchMethodError: EntityManager.createQuery(CriteriaQuery)`.
  This is an ecosystem-timing blocker for the starter, independent of this
  branch's changes.
- The extension defaults `hibernate.flush.queue.type` to `legacy`
  (see the service contributor in
  `src/main/java/com/mongodb/hibernate/internal/service/StandardServiceRegistryScopedState.java`).
  Hibernate 8's default graph-based flush queue does not batch entity deletes
  and issues extra JDBC metadata probes from the session path. The default is
  removable once delete batching works in the graph-based queue.

## Commit series

1. SPI migration: package moves (`sql.ast.tree` to `sql.ast.spi.query`, the
   `sql.model` split, `SqlAppender` to `sql.spi`), the request-based
   `SqlAstTranslatorFactory`, Dialect hook ports (`contributeTypes`,
   `ArraySupport`, `TemporalFormatSupport`, `UniqueDelegate`,
   request-based `createOptionalTableUpdateOperation`), and the
   `JdbcLockingApplication`/`JdbcPaginationApplication` select constructor.
2. Behavioral fixes found by the test run: the QueryOptions flush-mode guard
   (8.0 returns the JPA default instead of null-unless-set, so the guard
   rejected every boot), and the rejecting upsert operation's
   parameter-descriptor table.
3. The flush-queue legacy pin described above.
4. `AbstractMqlTranslator` extends the classified `AbstractSqlAstWalker`
   instead of implementing `SqlAstWalker` directly.
5. JDBC operation construction ported to the new
   `org.hibernate.sql.exec.spi.JdbcOperations` factory; the translators hold
   their `SqlAstTranslationRequest`.
6. Ports off internals with supported replacements:
   `StandardAggregateSupport`, `StandardDdlTypes`, `QueryOptions.NONE`,
   and the boot-model `Component` check instead of `ComponentType`.
7. Interface-surfacing conversions: the offset and limit parameters implement
   `org.hibernate.sql.ast.spi.query.expression.JdbcParameter` and
   `org.hibernate.sql.exec.spi.JdbcParameterBinder` directly; field-path
   resolution goes through `Expression.getColumnReference()`; aggregate
   recognition through `EmbeddableValuedModelPart`; the rejecting upsert
   implements `org.hibernate.sql.spi.mutation.jdbc.JdbcValueDescriptor`;
   parameter member reads dispatch through
   `org.hibernate.sql.ast.spi.query.expression.JdbcParameter` and
   `SqlExpressible`.
8. `SqlTreePrinter` debug logging dropped (internal utility, no equivalent).
9. The no-op update (`TableUpdateNoSet`) detected through the spi
   `TableUpdate` accessors instead of the internal marker class.
10. The struct flatten and assemble walks copied from `StructHelper` into
    `MongoStructJdbcType` as private static methods (later superseded by
    entry 11),
    reduced to what the type uses: no attribute-order mapping, no
    polymorphic embeddables, and associations decomposed through the
    public `ModelPart` contract. The values holder implements the
    incubating `org.hibernate.metamodel.spi.ValueAccess`, the one finding the copy adds.
11. The struct type reshaped onto `AggregateJdbcValues`, following the
    `ExampleStructuredJdbcType` fixture: physical, driver-shaped component
    values in, `toLogicalJdbcValues`/`toDomainValue` out, with the copied
    walks deleted. A temporary local bridge in `MongoArrayJdbcType` around
    the upstream double-wrap bug was removed once the fix landed in
    PR 13302.

## Provider-boundary report

`./gradlew validateDialectProviderBoundaries` against this branch's jar
(using the plugin from the PR, with classification metadata generated from
the branch checkout): 50 errors and 8 warnings.

The 50 errors (`MISSING_IMPLEMENT_ROLE`, 18 declarations) are the deliberate
output of the interface-surfacing series: every internal dependency that
could be expressed against an spi interface was converted, so the report
names exactly the contracts that need classification. Per the generated
classification metadata, the 18 declarations fall into two categories.

Classified SPI, `USE` role only (11 declarations). The category is right;
implementing them simply needs the `IMPLEMENT` role:

- `org.hibernate.service.spi.ServiceInitiator`
- `org.hibernate.service.spi.ServiceContributor`
- `org.hibernate.service.spi.Stoppable`
- `org.hibernate.service.spi.Wrapped`
- `org.hibernate.boot.registry.selector.spi.NamedStrategyContributor`
- `org.hibernate.boot.spi.AdditionalMappingContributor`
- `org.hibernate.engine.jdbc.connections.spi.ConnectionProvider`
- `org.hibernate.engine.jdbc.connections.spi.DatabaseConnectionInfo`
- `org.hibernate.sql.spi.mutation.SelfExecutingUpdateOperation`
- `org.hibernate.sql.spi.mutation.jdbc.JdbcValueDescriptor`
- `org.hibernate.sql.spi.mutation.MutationOperation`

Classified API with no roles (7 declarations). These are public types in
plain, non-spi packages, and the classifier resolved them as
application-facing contracts; under the model, a provider implementing an
API declaration is a policy violation. Custom identifier generators and
custom service initiators have been documented extension points for years,
so this looks like the unannotated-public-type defaulting rule sweeping up
classic extension points rather than intent. These need reclassification
to SPI with `IMPLEMENT`, not just a role:

- `org.hibernate.service.Service`
- `org.hibernate.boot.registry.StandardServiceInitiator`
- `org.hibernate.generator.Generator`
- `org.hibernate.generator.BeforeExecutionGenerator`
- `org.hibernate.query.sqm.function.SetReturningFunctionRenderer`
- `org.hibernate.query.sqm.function.AbstractSqmSelfRenderingSetReturningFunctionDescriptor`
- `org.hibernate.dialect.function.array.AbstractArrayIncludesFunction`

Hibernate's own `ConnectionProvider` (SPI, `USE`) extending `Service`
(API) is a cross-category edge of the kind their
`FORBIDDEN_CATEGORY_DEPENDENCY` validation is meant to catch, which is
further evidence the API classifications are unintended.

Three upstream changes landed after this list was first compiled.
`JdbcParameterFactory` (`queryLimit`, `queryOffset`, `custom`) replaced
our own offset and limit parameter implementations, removing the four
parameter-surface declarations above. And the boundary analyzer now
accepts a provider-owned SPI declaration (a type in a provider `spi`
package) composing Hibernate API, which cleared the `Service`
implementation on our `cfg.spi` `MongoConfigurationContributor`; the
remaining `Service` finding is the one in an internal package. And
`AggregateJdbcValues`/`AggregateJdbcValueOrder` plus the
`ExampleStructuredJdbcType` provider fixture gave the struct type a
supported round trip, which removed the `ValueAccess` declaration above
along with the copied `StructHelper` walks (commit b5d3a22f).

The six remaining warning declarations have no local route; they are
runtime types Hibernate instantiates and hands to the extension:

- `org.hibernate.persister.entity.JoinedSubclassEntityPersister`,
  `org.hibernate.persister.entity.SingleTableEntityPersister`,
  `org.hibernate.persister.entity.UnionSubclassEntityPersister`
  (used only to select which unsupported-feature error to throw: an
  entity spanning multiple query spaces is rejected either way, and the
  concrete class picks the JOINED, TABLE_PER_CLASS, or @SecondaryTable
  message)
- `org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation`
  (recognition; needs a hook or classification)
- `org.hibernate.boot.registry.StandardServiceRegistryBuilder#getSettings()`
  (the one settings read accessor, deliberately `@Internal`, called by our
  service contributor; the builder's writes are supported API)
- `org.hibernate.engine.jdbc.connections.spi.DatabaseConnectionInfo` (the
  extension implements the interface; the warnings are the overrides of
  `hasSchema()` and `hasCatalog()`, which are deliberately `@Internal` and
  abstract, so no implementer can compile without them)

## Upstream asks

1. The `IMPLEMENT` role for the 16 SPI-classified declarations above, and
   reclassification of the 7 API-classified ones (the defaulting rule for
   unannotated public types in plain packages, or deliberate
   reclassification of the classic extension points among them).
2. Landed: `JdbcParameterFactory` (`queryLimit`, `queryOffset`, `custom`)
   and `ColumnValueParameter` declaring its JDBC-parameter methods as its
   provider-facing contract. Our parameter classes and interface casts are
   gone.
3. A way to distinguish the three inheritance strategies on the entity
   mapping contract. Today the extension uses the concrete persisters only
   to pick which unsupported-feature error to throw when an entity spans
   multiple query spaces, so the present need is precise messages, nothing
   functional. This is the one ask that anticipates future work: when
   JOINED or @SecondaryTable support is designed, the strategy branch then
   decides how the query is translated, and the right shape for the
   accessor depends on that design. The subclass structure and the
   per-subclass table mappings the feature would need are already reachable
   through the `EntityMappingType` mapping SPI; the strategy value is the
   piece with no supported source.
4. `isParameterInterpretation(Expression)` exposed beyond
   `AbstractSqlAstTranslator` (a static utility or a default method on the
   `SqlAstTranslator` interface). It is already the sanctioned recognition
   for query-parameter operands, but as a protected final member of the
   SQL-rendering base it is unavailable to direct implementations, which
   otherwise must name the internal
   `org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation` to
   recognize them.
5. A supported way for a `ServiceContributor` to read the current
   settings. The builder it is handed classifies as API and its
   `applySetting`/`addInitiator` are supported, but its only read accessor,
   `getSettings()`, is deliberately `@Internal`; a contributor that
   auto-configures must read before it writes. Un-mark `getSettings()` or
   hand the contributor a supported settings view. Separately, for
   `org.hibernate.engine.jdbc.connections.spi.DatabaseConnectionInfo`:
   the `IMPLEMENT` role, and either un-marked or defaulted
   `hasSchema()`/`hasCatalog()`. As it stands the interface is
   unimplementable by a provider without findings, because those two
   members are both `@Internal` and abstract.
6. Fixed by Steve in PR 13302: `StructHelper#wrapRawJdbcValue` is now
   idempotent for array values, so `ArrayJdbcType#toJavaArray` no longer
   double-wraps array components of structured elements. Our reproducer
   test is absorbed into the PR (with a direct-Java-Time variant Steve
   added), the fix was verified against our extension, and the local
   bridge is removed (commit 3bfa38f9).
7. One structural finding remains: `Dialect#contributeDefaultProperties`
   cannot influence `hibernate.flush.queue.type` because a service initiator
   consumes that setting before Dialect defaults merge.

8. A main-line regression (from the nullability-annotations pass, present
   in the 8.1 snapshot via main, not from PR 13302 itself): the pass added
   `assert instance != null` to `EntityDeleteAction#execute`, which
   contradicts both the id-only constructor (a null instance is its
   contract for removing an unloaded reference) and `execute`'s own
   `postDeleteUnloaded` branch. Any
   `session.remove(session.getReference(...))` fails under an
   assertions-enabled JVM (Gradle test tasks default to `-ea`) on the
   legacy queue; main's default graph queue never runs that code, which
   is why Hibernate's own suite does not catch it. The assert should be
   dropped.
9. PR 13302 flips `hibernate.type.java_time_use_direct_jdbc` to default
   true and adds the `DirectJavaTimeJdbcSupport` supply point (the
   Dialect default is `jdbc42`). Our suite is green with the flipped
   default, but our boot guard forbids configuring the property, so the
   only escape hatch for MongoDB users is gone; whether MongoDB should
   supply a more precise `DirectJavaTimeJdbcSupport` than `jdbc42` is an
   open product decision.
7. The graph-based flush queue regression: entity deletes execute one
   statement per row instead of batching, observable to any driver.

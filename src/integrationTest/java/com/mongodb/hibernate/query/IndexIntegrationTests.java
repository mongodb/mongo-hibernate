/*
 * Copyright 2026-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.hibernate.query;

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_CONFIGURATION_CONTRIBUTOR_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.CommandHistory;
import com.mongodb.hibernate.junit.InjectCommandHistory;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.hibernate.AnnotationException;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Schema export of {@link Index} and {@link UniqueConstraint} declarations to MongoDB {@code createIndexes}.
 *
 * <p>MongoDB has no constraint objects, so a unique constraint is a unique index. Every assertion here is therefore of
 * one form: which indexes exist on the collection, over which fields, in which order, and which of them are unique.
 *
 * <p>Field order and per-field direction are both asserted because both are load-bearing in MQL: on a compound index
 * they together decide which sorts and which prefix queries the index can serve. Note that {@link BsonDocument}
 * implements {@link Map}, so comparing key documents with {@code equals} would silently ignore order.
 * {@link #indexesOf} flattens each key document to an ordered list to avoid that.
 *
 * <p>Each group of declarations gets its own entity and collection, one per test, for two reasons. Every test asserts
 * the complete index set for its collection, so bundling unrelated declarations would let one defect hide the state of
 * everything else. And MongoDB rejects a second index with the same key pattern under a different name
 * ({@code IndexOptionsConflict}), so declarations that overlap on fields cannot share a collection.
 */
@ExtendWith(MongoExtension.class)
class IndexIntegrationTests {

    // Hibernate's implicit naming strategy derives these from the table and column names, so renaming either changes
    // them. They are asserted rather than ignored so that a change in Hibernate's naming is visible here.
    private static final String COLUMN_UNIQUE_NAME = "UK58wdc0id5yrcr2hgn1fis4txl";
    private static final String UNNAMED_INDEX_NAME = "IDX6eej5imjx4n2hikl5yyt8d14u";
    private static final String UNNAMED_UNIQUE_NAME = "UK4ut95qgekorog9ebigpnhjwa2";

    @InjectCommandHistory
    private CommandHistory commandHistory;

    /**
     * Drops the collections this class asserts on.
     *
     * <p>{@link MongoExtension} empties collections between tests but leaves collections and their indexes standing,
     * which is right for the document-level tests that make up the rest of the suite and not enough here: a test that
     * asserts a complete index set has to establish that precondition itself. Relying on the drop half of
     * {@code create-drop} having run is not sufficient, because it does not run when a {@code SessionFactory} fails to
     * open, which is exactly what the negative tests below provoke.
     */
    @BeforeEach
    void dropAssertedCollections() {
        List.of(
                        ascendingCollection,
                        descendingCollection,
                        constrainedCollection,
                        uniqueDescendingCollection,
                        columnUniqueCollection,
                        unnamedCollection,
                        qualifiedCollection)
                .forEach(MongoCollection::drop);
    }

    /**
     * The indexes this extension declared on the collection, as {@code name -> ordered key}, where each key entry is
     * {@code field:direction}. Ordered, so a reordered compound key or a flipped direction fails.
     *
     * <p>{@code _id_} is excluded. MongoDB creates it for every collection regardless of the mapping, so it says
     * nothing about schema export.
     */
    private static Map<String, List<String>> indexesOf(MongoCollection<BsonDocument> collection) {
        return collection.listIndexes(BsonDocument.class).into(new ArrayList<>()).stream()
                .filter(index -> !index.getString("name").getValue().equals("_id_"))
                .collect(Collectors.toMap(
                        index -> index.getString("name").getValue(),
                        index -> index.getDocument("key").entrySet().stream()
                                .map(field -> field.getKey() + ":"
                                        + field.getValue().asNumber().intValue())
                                .toList()));
    }

    /**
     * The names of the unique indexes on the collection, sorted. Note that {@code _id_} never appears: it is implicitly
     * unique, but MongoDB does not set the {@code unique} flag on it.
     */
    private static List<String> uniqueIndexesOf(MongoCollection<BsonDocument> collection) {
        return collection.listIndexes(BsonDocument.class).into(new ArrayList<>()).stream()
                .filter(index -> index.getBoolean("unique", BsonBoolean.FALSE).getValue())
                .map(index -> index.getString("name").getValue())
                .sorted()
                .toList();
    }

    /**
     * Boots a {@code SessionFactory} for {@code entityClass} with {@code create-drop}, runs {@code body} while it is
     * still open so the collection can be inspected before the drop half runs, and returns the commands sent.
     *
     * <p>The registry is built by hand rather than through Hibernate's testing framework, so it applies the contributor
     * itself. That is what points the {@code SessionFactory} at this class's own database, the one that
     * {@link InjectMongoCollection} reads, and what installs that database's command listener.
     */
    /**
     * What one schema export produced: the commands sent, and whatever {@code observer} looked at while it was open.
     */
    private record Export<T>(List<BsonDocument> commands, T observed) {}

    /** Every index on the collection and which of them are unique, captured together. */
    private record Indexes(Map<String, List<String>> keys, List<String> unique) {}

    private static Indexes indexesAndUniqueness(MongoCollection<BsonDocument> collection) {
        return new Indexes(indexesOf(collection), uniqueIndexesOf(collection));
    }

    private <T> Export<T> inRegistry(Class<?> entityClass, Function<Session, T> observer) {
        try (var registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of(
                        "jakarta.persistence.schema-generation.database.action",
                        "create-drop",
                        "hibernate.hbm2ddl.halt_on_error",
                        "true"))
                .applySetting(
                        MONGO_CONFIGURATION_CONTRIBUTOR_KEY,
                        MongoExtension.configurationContributorForClass(IndexIntegrationTests.class))
                .build()) {
            T observed;
            try (var sessionFactory = new MetadataSources()
                            .addAnnotatedClass(entityClass)
                            .buildMetadata(registry)
                            .buildSessionFactory();
                    var session = sessionFactory.openSession()) {
                observed = observer.apply(session);
            }
            return new Export<>(commandHistory.getCommands(), observed);
        }
    }

    /**
     * The {@code createIndexes} commands that were sent, as JSON, with the session and cluster metadata the driver adds
     * stripped off.
     *
     * <p>Compared as strings rather than as {@link BsonDocument}s because {@code BsonDocument} implements {@link Map},
     * so equality ignores field order, and the order of the fields in a compound key is significant.
     */
    private static List<String> createIndexesCommands(List<BsonDocument> commands) {
        return commands.stream()
                .filter(command -> command.containsKey("createIndexes"))
                .map(command -> new BsonDocument("createIndexes", command.get("createIndexes"))
                        .append("indexes", command.get("indexes"))
                        .toJson())
                .toList();
    }

    /**
     * The {@code createIndexes} command that should be emitted for one index, where each {@code key} entry is
     * {@code field:direction}.
     *
     * <p>{@code unique} is only present when true, because the driver omits it otherwise. One index per command, so
     * comparing against the full list also asserts that indexes are not batched together.
     */
    private static String createIndexes(String collection, String name, boolean unique, String... key) {
        var keys = new BsonDocument();
        for (var entry : key) {
            var field = entry.split(":");
            keys.append(field[0], new BsonInt32(Integer.parseInt(field[1])));
        }
        var index = new BsonDocument("key", keys).append("name", new BsonString(name));
        if (unique) {
            index.append("unique", BsonBoolean.TRUE);
        }
        return new BsonDocument("createIndexes", new BsonString(collection))
                .append("indexes", new BsonArray(List.of(index)))
                .toJson();
    }

    @InjectMongoCollection("ascending")
    private MongoCollection<BsonDocument> ascendingCollection;

    /** {@code @Index} with the direction left out and spelled out, over one field and over several. */
    @Test
    void ascendingIndexes() {
        var export = inRegistry(Ascending.class, session -> indexesAndUniqueness(ascendingCollection));

        assertThat(createIndexesCommands(export.commands()))
                .containsExactlyInAnyOrder(
                        createIndexes("ascending", "idx_implicit", false, "publishYear:1"),
                        createIndexes("ascending", "idx_explicit_asc", false, "edition:1"),
                        createIndexes("ascending", "idx_compound", false, "publisher:1", "author:1"));
        assertThat(export.observed().keys())
                .containsOnly(
                        Map.entry("idx_implicit", List.of("publishYear:1")),
                        Map.entry("idx_explicit_asc", List.of("edition:1")),
                        Map.entry("idx_compound", List.of("publisher:1", "author:1")));
        assertThat(export.observed().unique()).isEmpty();
    }

    @InjectMongoCollection("descending")
    private MongoCollection<BsonDocument> descendingCollection;

    /** {@code @Index} carrying {@code desc}, alone and mixed with {@code asc} in a compound key. */
    @Test
    void descendingIndexes() {
        var export = inRegistry(Descending.class, session -> indexesAndUniqueness(descendingCollection));

        assertThat(createIndexesCommands(export.commands()))
                .containsExactlyInAnyOrder(
                        createIndexes("descending", "idx_desc", false, "pageCount:-1"),
                        createIndexes("descending", "idx_compound_mixed", false, "language:-1", "translator:1"));
        assertThat(export.observed().keys())
                .containsOnly(
                        Map.entry("idx_desc", List.of("pageCount:-1")),
                        Map.entry("idx_compound_mixed", List.of("language:-1", "translator:1")));
        assertThat(export.observed().unique()).isEmpty();
    }

    @InjectMongoCollection("constrained")
    private MongoCollection<BsonDocument> constrainedCollection;

    /**
     * {@code @UniqueConstraint} over one column and over several. {@code columnNames} has no direction grammar, so
     * these are always ascending, and field order follows the array.
     */
    @Test
    void uniqueConstraints() {
        var export = inRegistry(Constrained.class, session -> indexesAndUniqueness(constrainedCollection));

        assertThat(createIndexesCommands(export.commands()))
                .containsExactlyInAnyOrder(
                        createIndexes("constrained", "uk_single", true, "sku:1"),
                        createIndexes("constrained", "uk_compound", true, "subtitle:1", "imprint:1"));
        assertThat(export.observed().keys())
                .containsOnly(
                        Map.entry("uk_single", List.of("sku:1")),
                        Map.entry("uk_compound", List.of("subtitle:1", "imprint:1")));
        assertThat(export.observed().unique()).containsExactly("uk_compound", "uk_single");
    }

    @InjectMongoCollection("unique_descending")
    private MongoCollection<BsonDocument> uniqueDescendingCollection;

    /**
     * {@code @Index(unique = true)} carrying {@code desc}. This is the only way to ask for a descending unique index,
     * since {@code @UniqueConstraint} cannot express direction. It binds to a {@code UniqueKey} rather than an
     * {@code Index}.
     */
    @Test
    void uniqueDescendingIndexes() {
        var export = inRegistry(UniqueDescending.class, session -> indexesAndUniqueness(uniqueDescendingCollection));

        assertThat(createIndexesCommands(export.commands()))
                .containsExactlyInAnyOrder(
                        createIndexes("unique_descending", "uk_desc", true, "isbn:-1"),
                        createIndexes("unique_descending", "uk_compound_mixed", true, "series:-1", "volume:1"));
        assertThat(export.observed().keys())
                .containsOnly(
                        Map.entry("uk_desc", List.of("isbn:-1")),
                        Map.entry("uk_compound_mixed", List.of("series:-1", "volume:1")));
        assertThat(export.observed().unique()).containsExactly("uk_compound_mixed", "uk_desc");
    }

    @InjectMongoCollection("column_unique")
    private MongoCollection<BsonDocument> columnUniqueCollection;

    /**
     * {@code @Column(unique = true)}, which carries no name of its own, so Hibernate generates one. It needs a field no
     * other uniqueness declaration touches, because two declarations over one column resolve to a single MongoDB key
     * pattern under two names, which the server rejects.
     */
    @Test
    void columnLevelUnique() {
        var export = inRegistry(ColumnUnique.class, session -> indexesAndUniqueness(columnUniqueCollection));

        assertThat(createIndexesCommands(export.commands()))
                .containsExactlyInAnyOrder(createIndexes("column_unique", COLUMN_UNIQUE_NAME, true, "shelfCode:1"));
        assertThat(export.observed().keys()).containsOnly(Map.entry(COLUMN_UNIQUE_NAME, List.of("shelfCode:1")));
        assertThat(export.observed().unique()).containsExactly(COLUMN_UNIQUE_NAME);
    }

    @InjectMongoCollection("unnamed")
    private MongoCollection<BsonDocument> unnamedCollection;

    /** An unnamed declaration takes its name from Hibernate's implicit naming strategy, never from the extension. */
    @Test
    void unnamedDeclarationsAreNamedByHibernate() {
        assertThat(inRegistry(Unnamed.class, session -> indexesOf(unnamedCollection))
                        .observed())
                .containsOnly(
                        Map.entry(UNNAMED_INDEX_NAME, List.of("title:1")),
                        Map.entry(UNNAMED_UNIQUE_NAME, List.of("code:1")));
    }

    @InjectMongoCollection("lib.tomes")
    private MongoCollection<BsonDocument> qualifiedCollection;

    /**
     * A schema qualifier folds into the collection name, so every command in the export has to name the same
     * collection. Indexes created against the unqualified name would land on an auto-created sibling collection that
     * holds none of the data, and nothing would report an error.
     */
    @Test
    void indexesFollowTheQualifiedCollectionName() {
        var export = inRegistry(Qualified.class, session -> indexesOf(qualifiedCollection));

        assertThat(createIndexesCommands(export.commands()))
                .containsExactly(createIndexes("lib.tomes", "idx_title", false, "title:1"));
        assertThat(export.observed()).containsOnly(Map.entry("idx_title", List.of("title:1")));
    }

    /** {@code create-drop} creates the collection when the {@code SessionFactory} opens and drops it when it closes. */
    @Test
    void createDropLifecycle() {
        var commands = inRegistry(Ascending.class, session -> null).commands();
        assertThat(commands.stream()
                        .filter(command -> command.containsKey("create"))
                        .map(command -> command.getString("create").getValue()))
                .containsOnly("ascending");
        assertThat(commands.stream()
                        .filter(command -> command.containsKey("drop"))
                        .map(command -> command.getString("drop").getValue()))
                .containsOnly("ascending");
    }

    @Nested
    class Unsupported {

        /** MongoDB has no equivalent of a trailing DDL fragment. */
        @Test
        void indexOptions() {
            assertThatThrownBy(() -> inRegistry(WithOptions.class, session -> null))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage("Index idx_options on with_options has options, which is not supported");
        }

        /**
         * A parenthesized {@code columnList} entry binds as a {@code Formula}, which is the only way to reach the
         * exporter's formula guard. Naming a {@code @Formula} property in {@code columnList} does not reach it, because
         * that resolves to a plain column.
         */
        @Test
        void formulaIndex() {
            assertThatThrownBy(() -> inRegistry(WithFormulaIndex.class, session -> null))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage(
                            "Index idx_formula on with_formula_index uses a formula column, which is not supported");
        }

        /** The same, for the one mapping in which {@code Index.isUnique()} is ever true. */
        @Test
        void uniqueFormulaIndex() {
            assertThatThrownBy(() -> inRegistry(WithUniqueFormulaIndex.class, session -> null))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage("Index idx_unique_formula on with_unique_formula_index uses a formula column, which is"
                            + " not supported");
        }
    }

    /**
     * Mappings that are not merely unsupported but wrong, so the failure is an invalid mapping rather than a missing
     * feature. Hibernate itself has the check for these, in {@code IndexBinder.selectable()} (`:66`) and
     * {@code IndexBinder.column()} (`:86`), but it is commented out in 7.4, so an unresolvable entry becomes a
     * {@code Column} carrying that literal name. A relational dialect is saved by the server rejecting DDL that names a
     * column which does not exist; MongoDB accepts any field name, so nothing reports an error and the index is built
     * over a field no document has.
     *
     * <p>The expected exception type follows Hibernate's own disabled check, which threw {@link AnnotationException}.
     * The message names only the table and the column. Naming the annotation, as that check did, would require knowing
     * which annotation the mapping came from, which is not always available.
     */
    @Nested
    class InvalidMappings {

        /** {@code columnList} naming a column that is not mapped. */
        @Test
        void indexOnUnmappedColumn() {
            assertThatThrownBy(() -> inRegistry(UnmappedIndexColumn.class, session -> null))
                    .isInstanceOf(AnnotationException.class)
                    .hasMessage("Table 'unmapped_index' has no column named 'noSuchColumn'");
        }

        /** {@code columnNames} naming a column that is not mapped. A different Hibernate code path from the above. */
        @Test
        void uniqueConstraintOnUnmappedColumn() {
            assertThatThrownBy(() -> inRegistry(UnmappedConstraintColumn.class, session -> null))
                    .isInstanceOf(AnnotationException.class)
                    .hasMessage("Table 'unmapped_constraint' has no column named 'noSuchColumn'");
        }
    }

    @Entity(name = "UnmappedIndexColumn")
    @Table(name = "unmapped_index", indexes = @Index(name = "idx_unmapped", columnList = "noSuchColumn"))
    static class UnmappedIndexColumn {
        @Id
        int id;

        String title;
    }

    @Entity(name = "UnmappedConstraintColumn")
    @Table(
            name = "unmapped_constraint",
            uniqueConstraints =
                    @UniqueConstraint(
                            name = "uk_unmapped",
                            columnNames = {"noSuchColumn"}))
    static class UnmappedConstraintColumn {
        @Id
        int id;

        String title;
    }

    @Entity(name = "Ascending")
    @Table(
            name = "ascending",
            indexes = {
                @Index(name = "idx_implicit", columnList = "publishYear"),
                @Index(name = "idx_explicit_asc", columnList = "edition asc"),
                @Index(name = "idx_compound", columnList = "publisher,author")
            })
    static class Ascending {
        @Id
        int id;

        int publishYear;
        int edition;
        String publisher;
        String author;
    }

    @Entity(name = "Descending")
    @Table(
            name = "descending",
            indexes = {
                @Index(name = "idx_desc", columnList = "pageCount desc"),
                @Index(name = "idx_compound_mixed", columnList = "language desc,translator asc")
            })
    static class Descending {
        @Id
        int id;

        int pageCount;
        String language;
        String translator;
    }

    @Entity(name = "Constrained")
    @Table(
            name = "constrained",
            uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_single",
                        columnNames = {"sku"}),
                @UniqueConstraint(
                        name = "uk_compound",
                        columnNames = {"subtitle", "imprint"})
            })
    static class Constrained {
        @Id
        int id;

        String sku;
        String subtitle;
        String imprint;
    }

    @Entity(name = "UniqueDescending")
    @Table(
            name = "unique_descending",
            indexes = {
                @Index(name = "uk_desc", columnList = "isbn desc", unique = true),
                @Index(name = "uk_compound_mixed", columnList = "series desc,volume asc", unique = true)
            })
    static class UniqueDescending {
        @Id
        int id;

        String isbn;
        String series;
        int volume;
    }

    @Entity(name = "ColumnUnique")
    @Table(name = "column_unique")
    static class ColumnUnique {
        @Id
        int id;

        @Column(unique = true)
        String shelfCode;
    }

    @Entity(name = "Unnamed")
    @Table(
            name = "unnamed",
            uniqueConstraints = @UniqueConstraint(columnNames = {"code"}),
            indexes = @Index(columnList = "title"))
    static class Unnamed {
        @Id
        int id;

        String title;
        String code;
    }

    @Entity(name = "Qualified")
    @Table(schema = "lib", name = "tomes", indexes = @Index(name = "idx_title", columnList = "title"))
    static class Qualified {
        @Id
        int id;

        String title;
    }

    @Entity(name = "WithOptions")
    @Table(
            name = "with_options",
            indexes = @Index(name = "idx_options", columnList = "publishYear", options = "something"))
    static class WithOptions {
        @Id
        int id;

        int publishYear;
    }

    @Entity(name = "WithFormulaIndex")
    @Table(name = "with_formula_index", indexes = @Index(name = "idx_formula", columnList = "(publishYear + 1)"))
    static class WithFormulaIndex {
        @Id
        int id;

        int publishYear;
    }

    @Entity(name = "WithUniqueFormulaIndex")
    @Table(
            name = "with_unique_formula_index",
            indexes = @Index(name = "idx_unique_formula", columnList = "(publishYear + 1)", unique = true))
    static class WithUniqueFormulaIndex {
        @Id
        int id;

        int publishYear;
    }
}

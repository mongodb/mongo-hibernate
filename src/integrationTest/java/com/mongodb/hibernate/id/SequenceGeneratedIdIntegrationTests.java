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

package com.mongodb.hibernate.id;

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_CONFIGURATION_CONTRIBUTOR_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.CommandHistory;
import com.mongodb.hibernate.junit.InjectCommandHistory;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bson.BsonDocument;
import org.hibernate.MappingException;
import org.hibernate.Session;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Sequence-style identifier generation, from schema export through allocation to drop.
 *
 * <p>Each test boots its own {@code SessionFactory} with {@code create-drop}. {@link MongoExtension} empties every
 * collection in this class's database before each test, {@code hibernate_sequences} among them, so a
 * {@code SessionFactory} shared across the class would leave later tests with no counter document. A pooled optimizer
 * also holds its block in the generator, which lives in the {@code SessionFactory}, so any assertion about round trips
 * needs a fresh one.
 */
@ExtendWith(MongoExtension.class)
class SequenceGeneratedIdIntegrationTests {

    private static final Map<String, Object> BASE_SETTINGS = Map.of(
            "jakarta.persistence.schema-generation.database.action",
            "create-drop",
            "hibernate.hbm2ddl.halt_on_error",
            "true");

    @InjectCommandHistory
    private CommandHistory commandHistory;

    @InjectMongoCollection("hibernate_sequences")
    private MongoCollection<BsonDocument> sequences;

    @InjectMongoCollection("books")
    private MongoCollection<BsonDocument> books;

    /** What one booted {@code SessionFactory} produced: the commands sent, and whatever the body observed. */
    private record Run<T>(List<BsonDocument> commands, T observed) {}

    /** The identifiers a persist-then-reload round trip observed, and the counter document at that point. */
    private record PersistedBook(long id, long reloadedId, BsonDocument sequenceDocument) {}

    /**
     * Boots a {@code SessionFactory} for {@code entityClasses} with {@code create-drop}, runs {@code body} while it is
     * open, and returns the commands sent along with the body's result.
     *
     * <p>The registry is built by hand so it can apply the contributor that points the {@code SessionFactory} at this
     * class's own database and installs that database's command listener.
     */
    private <T> Run<T> inRegistry(Function<Session, T> body, Class<?>... entityClasses) {
        return inRegistry(Map.of(), body, entityClasses);
    }

    private <T> Run<T> inRegistry(
            Map<String, Object> additionalSettings, Function<Session, T> body, Class<?>... entityClasses) {
        try (var registry = new StandardServiceRegistryBuilder()
                .applySettings(BASE_SETTINGS)
                .applySettings(additionalSettings)
                .applySetting(
                        MONGO_CONFIGURATION_CONTRIBUTOR_KEY,
                        MongoExtension.configurationContributorForClass(SequenceGeneratedIdIntegrationTests.class))
                .build()) {
            T observed;
            var metadataSources = new MetadataSources();
            for (var entityClass : entityClasses) {
                metadataSources.addAnnotatedClass(entityClass);
            }
            try (var sessionFactory = metadataSources.buildMetadata(registry).buildSessionFactory();
                    var session = sessionFactory.openSession()) {
                observed = body.apply(session);
            }
            return new Run<>(commandHistory.getCommands(), observed);
        }
    }

    /** The commands sent whose name matches {@code commandName}. */
    private static List<BsonDocument> commandsNamed(List<BsonDocument> commands, String commandName) {
        return commands.stream()
                .filter(command -> commandName.equals(command.getFirstKey()))
                .toList();
    }

    /**
     * Asserts that exactly one command named {@code commandName} was sent, and that it carries every field of
     * {@code expected}. A subset check rather than equality, because the driver adds session and cluster metadata
     * ({@code lsid}, {@code $db}, {@code txnNumber}, and the like) that {@code expected} does not name.
     */
    private static void assertOneCommandNamed(List<BsonDocument> commands, String commandName, String expected) {
        var matching = commandsNamed(commands, commandName);
        assertThat(matching).hasSize(1);
        assertThat(matching.get(0))
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsAllEntriesOf(BsonDocument.parse(expected));
    }

    @Entity(name = "Book")
    @Table(name = "books")
    static class Book {
        @Id
        @GeneratedValue
        Long id;

        String title;
    }

    /** A primitive identifier, to pin which class {@code getReturnedClass()} reports for one. */
    @Entity(name = "Pamphlet")
    @Table(name = "pamphlets")
    static class Pamphlet {
        @Id
        @GeneratedValue
        long id;

        String title;
    }

    @Entity(name = "Ledger")
    @Table(name = "ledgers")
    @SequenceGenerator(name = "ledgerSeq", allocationSize = 1)
    static class Ledger {
        @Id
        @GeneratedValue(generator = "ledgerSeq")
        Long id;
    }

    @Entity(name = "Invoice")
    @Table(name = "invoices")
    @SequenceGenerator(
            name = "invoice_numbers",
            sequenceName = "invoice_numbers",
            initialValue = 1000,
            allocationSize = 10)
    static class Invoice {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "invoice_numbers")
        Long id;
    }

    @Entity(name = "Receipt")
    @Table(name = "receipts")
    @SequenceGenerator(name = "receiptSeq", schema = "billing", sequenceName = "receipt_numbers", allocationSize = 1)
    static class Receipt {
        @Id
        @GeneratedValue(generator = "receiptSeq")
        Long id;
    }

    /**
     * Declares a generator that {@link SharedSequenceConsumer} references without declaring it itself. Both end up on
     * the default allocation size, which they must, since the consumer has no way to state one.
     */
    @Entity(name = "SharedSequenceDeclarer")
    @Table(name = "sharedSequenceDeclarers")
    @SequenceGenerator(name = "shared_seq", sequenceName = "shared_seq")
    static class SharedSequenceDeclarer {
        @Id
        @GeneratedValue(generator = "shared_seq")
        Long id;
    }

    @Entity(name = "SharedSequenceConsumer")
    @Table(name = "sharedSequenceConsumers")
    static class SharedSequenceConsumer {
        @Id
        @GeneratedValue(generator = "shared_seq")
        Long id;
    }

    @Entity(name = "Note")
    @Table(name = "notes")
    @SequenceGenerator(name = "noteSeq", allocationSize = 1)
    static class Note {
        @Id
        @GeneratedValue(generator = "noteSeq")
        Integer id;
    }

    @Test
    void persistsAndReloadsWithAGeneratedIdentifier() {
        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var book = new Book();
                    book.title = "War and Peace";
                    session.persist(book);
                    session.getTransaction().commit();
                    session.clear();
                    var reloaded = session.find(Book.class, book.id);
                    assertThat(reloaded.title).isEqualTo("War and Peace");
                    // Read while the SessionFactory is still open: create-drop deletes this document when it closes.
                    var sequenceDocument = sequences
                            .find(BsonDocument.parse("{\"_id\": \"books_SEQ\"}"))
                            .first();
                    return new PersistedBook(book.id, reloaded.id, sequenceDocument);
                },
                Book.class);

        assertThat(run.observed().id()).isEqualTo(1L);
        assertThat(run.observed().reloadedId()).isEqualTo(1L);
        assertThat(run.observed().sequenceDocument())
                .isEqualTo(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": "books_SEQ",
                                  "next_value": {"$numberLong": "51"},
                                  "increment": {"$numberLong": "50"}
                                }"""));

        assertOneCommandNamed(
                run.commands(),
                "update",
                """
                {
                  "update": "hibernate_sequences",
                  "updates": [
                    {
                      "q": {"_id": "books_SEQ"},
                      "u": {
                        "$setOnInsert": {
                          "next_value": {"$numberLong": "1"},
                          "increment": {"$numberLong": "50"}
                        }
                      },
                      "upsert": true
                    }
                  ]
                }""");
        assertOneCommandNamed(
                run.commands(),
                "findAndModify",
                """
                {
                  "findAndModify": "hibernate_sequences",
                  "query": {
                    "_id": "books_SEQ",
                    "next_value": {"$type": "long"},
                    "increment": {"$type": "long"}
                  },
                  "update": [
                    {"$set": {"next_value": {"$add": ["$next_value", "$increment"]}}}
                  ],
                  "new": false,
                  "fields": {"_id": 0, "next_value": 1}
                }""");
        assertThat(commandsNamed(run.commands(), "findAndModify").get(0)).doesNotContainKey("nonTransactional");

        // Two, not one: the schema tool's "create" action does a defensive drop before creating, and "create-drop"
        // drops again at teardown, so the sequence's drop command is sent twice with identical content.
        var deleteCommands = commandsNamed(run.commands(), "delete");
        assertThat(deleteCommands).hasSize(2);
        var expectedDelete = BsonDocument.parse(
                """
                {
                  "delete": "hibernate_sequences",
                  "deletes": [
                    {"q": {"_id": "books_SEQ"}, "limit": 1}
                  ]
                }""");
        assertThat(deleteCommands).allSatisfy(command -> assertThat(command)
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsAllEntriesOf(expectedDelete));
    }

    @Test
    void persistsWithAPrimitiveGeneratedIdentifier() {
        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var pamphlet = new Pamphlet();
                    pamphlet.title = "On Liberty";
                    session.persist(pamphlet);
                    session.getTransaction().commit();
                    return pamphlet.id;
                },
                Pamphlet.class);

        assertThat(run.observed()).isEqualTo(1L);
    }

    @Test
    void persistsWithAnIntegerGeneratedIdentifier() {
        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var note = new Note();
                    session.persist(note);
                    session.getTransaction().commit();
                    return note.id;
                },
                Note.class);

        assertThat(run.observed()).isEqualTo(1);
    }

    @Test
    void allocationSizeOneAllocatesPerRow() {
        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var ids = new ArrayList<Long>();
                    for (var i = 0; i < 3; i++) {
                        var ledger = new Ledger();
                        session.persist(ledger);
                        ids.add(ledger.id);
                    }
                    session.getTransaction().commit();
                    return ids;
                },
                Ledger.class);

        assertThat(run.observed()).containsExactly(1L, 2L, 3L);
        assertThat(commandsNamed(run.commands(), "findAndModify")).hasSize(3);
    }

    @Test
    void pooledAllocationAmortizesTheRoundTrip() {
        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var ids = new ArrayList<Long>();
                    for (var i = 0; i < 51; i++) {
                        var book = new Book();
                        book.title = "Volume " + i;
                        session.persist(book);
                        ids.add(book.id);
                    }
                    session.getTransaction().commit();
                    return ids;
                },
                Book.class);

        assertThat(run.observed())
                .isEqualTo(LongStream.rangeClosed(1, 51).boxed().toList());
        assertThat(commandsNamed(run.commands(), "findAndModify")).hasSize(2);
    }

    /**
     * {@link #allocationSizeOneAllocatesPerRow()} and {@link #pooledAllocationAmortizesTheRoundTrip()} assert exact
     * identifiers for {@code none} and {@code pooled}. These three produce different numbers: {@code hilo} multiplies
     * the stored value by the increment. This test checks only that identifiers are distinct and increasing.
     */
    @ParameterizedTest
    @ValueSource(strings = {"pooled-lo", "hilo", "legacy-hilo"})
    void optimizersYieldDistinctIncreasingIdentifiers(String optimizer) {
        var run = inRegistry(
                Map.of("hibernate.id.optimizer.pooled.preferred", optimizer),
                session -> {
                    session.getTransaction().begin();
                    var ids = new ArrayList<Long>();
                    for (var i = 0; i < 51; i++) {
                        var book = new Book();
                        book.title = "Volume " + i;
                        session.persist(book);
                        ids.add(book.id);
                    }
                    session.getTransaction().commit();
                    return ids;
                },
                Book.class);

        assertThat(run.observed()).doesNotHaveDuplicates().isSorted();
    }

    /**
     * JPA generator names are global, so an entity may reference a {@link SequenceGenerator} declared on a different
     * entity. {@code forbidUnintrospectableGenerator} rejects a {@code generator()} name it cannot resolve, and this is
     * the only shape that reaches its global-registration lookup rather than the member or class lookup.
     */
    @Test
    void aGeneratorNamedOnAnotherEntityIsAccepted() {
        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var consumer = new SharedSequenceConsumer();
                    session.persist(consumer);
                    session.getTransaction().commit();
                    return consumer.id;
                },
                SharedSequenceDeclarer.class,
                SharedSequenceConsumer.class);

        assertThat(run.observed()).isEqualTo(1L);
    }

    @Test
    void namedSequenceSeedsAtItsInitialValue() {
        record Result(long id, BsonDocument sequenceDocument) {}

        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var invoice = new Invoice();
                    session.persist(invoice);
                    session.getTransaction().commit();
                    // Read while the SessionFactory is still open: create-drop deletes this document when it closes.
                    var sequenceDocument = sequences
                            .find(BsonDocument.parse("{\"_id\": \"invoice_numbers\"}"))
                            .first();
                    return new Result(invoice.id, sequenceDocument);
                },
                Invoice.class);

        assertThat(run.observed().id()).isEqualTo(1000L);
        assertThat(run.observed().sequenceDocument())
                .isEqualTo(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": "invoice_numbers",
                                  "next_value": {"$numberLong": "1010"},
                                  "increment": {"$numberLong": "10"}
                                }"""));
    }

    @Test
    void schemaQualifiedSequenceNameFoldsIntoTheCounterId() {
        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var receipt = new Receipt();
                    session.persist(receipt);
                    session.getTransaction().commit();
                    // Read while the SessionFactory is still open: create-drop deletes this document when it closes.
                    return sequences
                            .find(BsonDocument.parse("{\"_id\": \"billing.receipt_numbers\"}"))
                            .first();
                },
                Receipt.class);

        assertThat(run.observed()).isNotNull();
    }

    @Test
    void reExportDoesNotResetALiveSequence() {
        sequences.insertOne(
                BsonDocument.parse(
                        """
                        {
                          "_id": "books_SEQ",
                          "next_value": {"$numberLong": "501"},
                          "increment": {"$numberLong": "50"}
                        }"""));

        // The JPA action "create" (unlike "create-drop") does not drop before creating, so re-export is the only
        // thing under test here: no teardown-drop to muddy whether the counter merely survived being untouched.
        var run = inRegistry(
                Map.of("jakarta.persistence.schema-generation.database.action", "create"),
                session -> {
                    session.getTransaction().begin();
                    var book = new Book();
                    book.title = "After re-export";
                    session.persist(book);
                    session.getTransaction().commit();
                    return book.id;
                },
                Book.class);

        // The pooled optimizer reads the returned value as the upper bound of the block it just reserved, the same
        // as a native sequence would: the fresh optimizer's first identifier is next_value - increment + 1 = 452,
        // not 501.
        assertThat(run.observed()).isEqualTo(452L);
    }

    @Test
    void allocationSurvivesARolledBackTransaction() {
        record Result(long id, long bookCount, long nextValue) {}

        // Read while the SessionFactory is still open: create-drop drops the "books" collection and deletes the
        // sequence document when it closes, so the row count would then read zero whatever rollback did.
        var run = inRegistry(
                session -> {
                    session.getTransaction().begin();
                    var book = new Book();
                    book.title = "Never committed";
                    session.persist(book);
                    session.getTransaction().rollback();
                    var nextValue = sequences
                            .find(BsonDocument.parse("{\"_id\": \"books_SEQ\"}"))
                            .first()
                            .getInt64("next_value")
                            .getValue();
                    return new Result(book.id, books.countDocuments(), nextValue);
                },
                Book.class);

        assertThat(run.observed().id()).isEqualTo(1L);
        assertThat(run.observed().bookCount()).isZero();
        assertThat(run.observed().nextValue()).isEqualTo(51L);
    }

    @Test
    void concurrentAllocationYieldsDistinctIdentifiers() throws Exception {
        var threads = 8;
        var perThread = 20;
        var run = inRegistry(
                session -> {
                    var sessionFactory = session.getSessionFactory();
                    var ids = Collections.synchronizedList(new ArrayList<Long>());
                    var executor = Executors.newFixedThreadPool(threads);
                    try {
                        var futures = new ArrayList<Future<?>>();
                        for (var t = 0; t < threads; t++) {
                            futures.add(executor.submit(() -> {
                                try (var threadSession = sessionFactory.openSession()) {
                                    for (var i = 0; i < perThread; i++) {
                                        threadSession.getTransaction().begin();
                                        var ledger = new Ledger();
                                        threadSession.persist(ledger);
                                        threadSession.getTransaction().commit();
                                        ids.add(ledger.id);
                                    }
                                }
                            }));
                        }
                        for (var future : futures) {
                            try {
                                future.get();
                            } catch (Exception e) {
                                throw new AssertionError("concurrent allocation failed", e);
                            }
                        }
                    } finally {
                        executor.shutdown();
                    }
                    return ids;
                },
                Ledger.class);

        assertThat(run.observed()).hasSize(threads * perThread).doesNotHaveDuplicates();
    }

    /**
     * A counter document created by hand, which a deployment with schema management turned off has to do, is rejected
     * unless it carries both 64-bit fields. Left unguarded, a missing {@code increment} makes {@code $add} evaluate to
     * null, so the sequence writes null over its own value and hands out the same identifier forever.
     *
     * <p>Each shape is caught in a different place, which is why each asserts its own message. A missing
     * {@code increment} reads back as {@code 0} during Hibernate ORM's boot-time increment-mismatch check and fails
     * there. A wrong BSON type fails in the same check, while reading the value. A missing {@code next_value} leaves
     * {@code increment} matching {@code allocationSize}, so boot succeeds and the allocation's own shape guard catches
     * it instead.
     */
    @ParameterizedTest
    @MethodSource("malformedCounterDocuments")
    void allocationFromAMalformedCounterFails(String counterDocument, String expectedMessage) {
        sequences.insertOne(BsonDocument.parse(counterDocument));

        assertThatThrownBy(() -> inRegistry(
                        Map.of("jakarta.persistence.schema-generation.database.action", "none"),
                        session -> {
                            session.getTransaction().begin();
                            var book = new Book();
                            book.title = "Malformed counter";
                            session.persist(book);
                            session.getTransaction().commit();
                            return null;
                        },
                        Book.class))
                .hasStackTraceContaining(expectedMessage);

        assertThat(sequences
                        .find(BsonDocument.parse("{\"_id\": \"books_SEQ\"}"))
                        .first())
                .isEqualTo(BsonDocument.parse(counterDocument));
    }

    private static Stream<Arguments> malformedCounterDocuments() {
        return Stream.of(
                arguments(
                        """
                        {"_id": "books_SEQ", "next_value": {"$numberLong": "1"}}""",
                        "database sequence increment size is [0]"),
                arguments(
                        """
                        {"_id": "books_SEQ", "increment": {"$numberLong": "50"}}""",
                        "matched no document"),
                arguments(
                        """
                        {"_id": "books_SEQ", "next_value": 1, "increment": 50}""",
                        "Value expected to be of type INT64 is of unexpected type INT32"));
    }

    /**
     * A counter document created by hand, the way a deployment with schema management turned off has to, may declare an
     * {@code increment} that does not match the generator's {@code allocationSize}. Hibernate ORM's own
     * increment-mismatch check catches this at boot, provided the dialect supplies sequence metadata; left unguarded,
     * the pooled optimizer would otherwise hand out negative identifiers once its first block is exhausted.
     */
    @Test
    void mismatchedIncrementFailsAtBoot() {
        sequences.insertOne(
                BsonDocument.parse(
                        """
                        {"_id": "books_SEQ", "next_value": {"$numberLong": "1"}, "increment": {"$numberLong": "1"}}"""));

        assertThatThrownBy(() -> inRegistry(
                        Map.of("jakarta.persistence.schema-generation.database.action", "none"),
                        session -> null,
                        Book.class))
                .isInstanceOf(MappingException.class)
                .hasMessage(
                        "The increment size of the [books_SEQ] sequence is set to [50] in the entity mapping but the"
                                + " mapped database sequence increment size is [1]");
    }

    @Test
    void allocationWithoutASeededCounterFails() {
        assertThatThrownBy(() -> inRegistry(
                        Map.of("jakarta.persistence.schema-generation.database.action", "none"),
                        session -> {
                            session.getTransaction().begin();
                            var book = new Book();
                            book.title = "No counter";
                            session.persist(book);
                            session.getTransaction().commit();
                            return null;
                        },
                        Book.class))
                .rootCause()
                .hasMessageContaining("findAndModify")
                .hasMessageContaining("books_SEQ");
    }

    @Nested
    class Unsupported {

        @Entity(name = "IdentityItem")
        @Table(name = "identityItems")
        static class IdentityItem {
            @Id
            @GeneratedValue(strategy = GenerationType.IDENTITY)
            Long id;
        }

        @Entity(name = "TableItem")
        @Table(name = "tableItems")
        static class TableItem {
            @Id
            @GeneratedValue(strategy = GenerationType.TABLE)
            Long id;
        }

        @Entity(name = "UuidItem")
        @Table(name = "uuidItems")
        static class UuidItem {
            @Id
            @GeneratedValue(strategy = GenerationType.UUID)
            String id;
        }

        @Entity(name = "BigIntegerItem")
        @Table(name = "bigIntegerItems")
        static class BigIntegerItem {
            @Id
            @GeneratedValue
            BigInteger id;
        }

        /**
         * Hibernate ORM reads a three-part sequence name as catalog, schema, sequence. This dialect reports only
         * SCHEMA, so the catalog would be dropped and the counter key would collide with a plain {@code schema = "b"},
         * {@code sequenceName = "c"}.
         */
        @Entity(name = "CatalogQualifiedItem")
        @Table(name = "catalogQualifiedItems")
        @SequenceGenerator(name = "catalogQualifiedSeq", sequenceName = "a.b.c", allocationSize = 1)
        static class CatalogQualifiedItem {
            @Id
            @GeneratedValue(generator = "catalogQualifiedSeq")
            Long id;
        }

        @Entity(name = "CatalogAttributeItem")
        @Table(name = "catalogAttributeItems")
        @SequenceGenerator(name = "catalogAttributeSeq", catalog = "cat", sequenceName = "s", allocationSize = 1)
        static class CatalogAttributeItem {
            @Id
            @GeneratedValue(generator = "catalogAttributeSeq")
            Long id;
        }

        /**
         * A '.' in the schema survives as a quoted identifier, so the counter key {@code a.b.c} would be
         * indistinguishable from a schema {@code a.b} plus sequence {@code c} written any other way.
         */
        @Entity(name = "DottedSequenceSchemaItem")
        @Table(name = "dottedSequenceSchemaItems")
        @SequenceGenerator(name = "dottedSchemaSeq", schema = "a.b", sequenceName = "c", allocationSize = 1)
        static class DottedSequenceSchemaItem {
            @Id
            @GeneratedValue(generator = "dottedSchemaSeq")
            Long id;
        }

        /**
         * Backtick-quoting stops Hibernate ORM from splitting the name on the '.', so the counter key {@code b.c} would
         * be indistinguishable from schema {@code b} plus sequence {@code c}.
         */
        @Entity(name = "QuotedDottedSequenceNameItem")
        @Table(name = "quotedDottedSequenceNameItems")
        @SequenceGenerator(name = "quotedDottedSeq", sequenceName = "`b.c`", allocationSize = 1)
        static class QuotedDottedSequenceNameItem {
            @Id
            @GeneratedValue(generator = "quotedDottedSeq")
            Long id;
        }

        @Entity(name = "OptionsItem")
        @Table(name = "optionsItems")
        @SequenceGenerator(name = "optionsSeq", options = "cache 20")
        static class OptionsItem {
            @Id
            @GeneratedValue(generator = "optionsSeq")
            Long id;
        }

        @Entity(name = "SequenceCollectionItem")
        @Table(name = "hibernate_sequences")
        static class SequenceCollectionItem {
            @Id
            Long id;
        }

        /**
         * {@code hiloOptimizersYieldDistinctMonotonicallyIncreasingIdentifiers} covers the working way to select
         * {@code hilo}: the {@code hibernate.id.optimizer.pooled.preferred} setting, which needs no generator
         * annotation at all. This entity instead selects it per-generator, the only way to do that being an explicit
         * {@code optimizer} parameter carried by {@code @GenericGenerator}, which
         * {@code forbidUnintrospectableGenerator} rejects outright alongside {@code @GeneratedValue}.
         */
        @SuppressWarnings("removal")
        @GenericGenerator(
                name = "hiloItemSeq",
                strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
                parameters = {
                    @Parameter(name = "optimizer", value = "hilo"),
                    @Parameter(name = "increment_size", value = "20")
                })
        @Entity(name = "HiloItem")
        @Table(name = "hiloItems")
        static class HiloItem {
            @Id
            @GeneratedValue(generator = "hiloItemSeq")
            Long id;
        }

        @Entity(name = "TableGeneratorItem")
        @Table(name = "tableGeneratorItems")
        static class TableGeneratorItem {
            @Id
            @GeneratedValue
            @TableGenerator(name = "tableGeneratorItemSeq", table = "table_generator_item_seq")
            Long id;
        }

        @Entity(name = "IdentityNamedGeneratorItem")
        @Table(name = "identityNamedGeneratorItems")
        static class IdentityNamedGeneratorItem {
            @Id
            @GeneratedValue(generator = "identity")
            Long id;
        }

        @Entity(name = "IncrementNamedGeneratorItem")
        @Table(name = "incrementNamedGeneratorItems")
        static class IncrementNamedGeneratorItem {
            @Id
            @GeneratedValue(generator = "increment")
            Long id;
        }

        @Test
        void identityStrategyIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, IdentityItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("IDENTITY")
                    .hasMessageContaining("SEQUENCE");
        }

        @Test
        void tableStrategyIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, TableItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("TODO-HIBERNATE-252");
        }

        @Test
        void uuidStrategyIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, UuidItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("TODO-HIBERNATE-121");
        }

        @Test
        void bigIntegerIdentifierIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, BigIntegerItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("TODO-HIBERNATE-253");
        }

        @Test
        void catalogQualifiedSequenceIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, CatalogQualifiedItem.class))
                    .hasStackTraceContaining("qualified by the catalog [a]");
        }

        @Test
        void catalogAttributeOnSequenceIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, CatalogAttributeItem.class))
                    .hasStackTraceContaining("qualified by the catalog [cat]");
        }

        @Test
        void dottedSequenceSchemaIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, DottedSequenceSchemaItem.class))
                    .hasStackTraceContaining("The character [.] in a sequence schema name is not supported");
        }

        @Test
        void dottedSequenceNameIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, QuotedDottedSequenceNameItem.class))
                    .hasStackTraceContaining("The character [.] in a sequence name is not supported");
        }

        @Test
        void sequenceOptionsAreRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, OptionsItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("cache 20");
        }

        @Test
        void entityMappedToTheSequenceCollectionIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, SequenceCollectionItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("hibernate_sequences");
        }

        @Test
        void hiloViaGenericGeneratorAnnotationIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, HiloItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("GenericGenerator");
        }

        @Test
        void localizedTableGeneratorIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, TableGeneratorItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("TODO-HIBERNATE-252");
        }

        @Test
        void namedIdentityGeneratorIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, IdentityNamedGeneratorItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("IDENTITY")
                    .hasMessageContaining("SEQUENCE");
        }

        @Test
        void namedIncrementGeneratorIsRejected() {
            assertThatThrownBy(() -> inRegistry(session -> null, IncrementNamedGeneratorItem.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("increment");
        }

        @Test
        void bulkInsertSelectIsRejected() {
            assertThatThrownBy(() -> inRegistry(
                            session -> {
                                session.getTransaction().begin();
                                try {
                                    return session.createMutationQuery(
                                                    "insert into Book (id, title) select b.id, b.title from Book b")
                                            .executeUpdate();
                                } finally {
                                    session.getTransaction().rollback();
                                }
                            },
                            Book.class))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("Insertion statement with source selection is not supported");
        }
    }
}

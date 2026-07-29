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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.CommandHistory;
import com.mongodb.hibernate.junit.InjectCommandHistory;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.annotations.Formula;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MongoExtension.class)
public class IndexIntegrationTests {
    @InjectMongoCollection("books")
    private MongoCollection<BsonDocument> booksCollection;

    @InjectCommandHistory
    protected CommandHistory commandHistory;

    private List<BsonDocument> inRegistry(Class<?> itemClass, Consumer<Session> body) {
        try (var registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of(
                        MONGO_CONFIGURATION_CONTRIBUTOR_KEY,
                        MongoExtension.configurationContributorForClass(this.getClass()),
                        "jakarta.persistence.schema-generation.database.action",
                        "create-drop",
                        "hibernate.hbm2ddl.halt_on_error",
                        "true"))
                .build()) {
            try (var sessionFactory = new MetadataSources()
                            .addAnnotatedClass(itemClass)
                            .buildMetadata(registry)
                            .buildSessionFactory();
                    var session = sessionFactory.openSession()) {
                body.accept(session);
            }
            return commandHistory.getCommands();
        }
    }

    @Test
    void testIndexCreated() {
        var commands = inRegistry(Book.class, session -> {
            var indexNames = new TreeSet<String>();
            booksCollection.listIndexes().forEach(index -> indexNames.add(index.getString("name")));
            assertEquals(
                    Set.of(
                            "IDXbeyw7jm8ev66e1mbr0hggy13e",
                            "_id_",
                            "idx_on_multi_cols",
                            "idx_on_single_col",
                            "uniq_idx_on_single_col"),
                    indexNames);
        });

        var createCommands = commands.stream()
                .filter(command -> command.containsKey("create"))
                .toList();
        var indexCommands = commands.stream()
                .filter(command -> command.containsKey("createIndexes"))
                .toList();
        var dropCommands =
                commands.stream().filter(command -> command.containsKey("drop")).toList();
        assertFalse(createCommands.isEmpty());
        assertEquals(4, indexCommands.size());
        assertFalse(dropCommands.isEmpty());

        assertThat(createCommands)
                .allSatisfy(command ->
                        assertThat(command.getString("create").getValue()).isEqualTo("books"));
        assertThat(indexCommands).allSatisfy(command -> assertThat(
                        command.getString("createIndexes").getValue())
                .isEqualTo("books"));
        assertThat(dropCommands)
                .allSatisfy(command ->
                        assertThat(command.getString("drop").getValue()).isEqualTo("books"));
        assertThat(indexCommands)
                .extracting(command -> command.getArray("indexes"))
                .allSatisfy(indexes -> assertThat(indexes).hasSize(1));
        // Note that the driver drops unique=false properties
        assertThat(indexCommands)
                .flatExtracting(command -> command.getArray("indexes"))
                .contains(
                        BsonDocument.parse("{ name: \"idx_on_single_col\", key: {publishYear: 1}}"),
                        BsonDocument.parse("{ name: \"idx_on_multi_cols\", key: {publisher: 1, author: 1}}"),
                        BsonDocument.parse("{ name: \"uniq_idx_on_single_col\", key: {isbn: -1}, unique: true}"),
                        BsonDocument.parse(
                                "{ name: \"IDXbeyw7jm8ev66e1mbr0hggy13e\", key: {publisher: 1, title: 1}, unique: true}"));
    }

    @Test
    void testGarbageDirection() {
        assertThatThrownBy(() -> inRegistry(InvalidDirection.class, session -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid index entry format: publishYear sideways");
    }

    @Entity(name = "Book")
    @Table(
            name = "books",
            uniqueConstraints = {
                @UniqueConstraint(
                        name = "uniq_idx_on_single_col",
                        columnNames = {"isbn desc"}),
            },
            indexes = {
                @Index(name = "idx_on_single_col", columnList = "publishYear asc"),
                @Index(name = "idx_on_multi_cols", columnList = "publisher,author"),
                @Index(columnList = "publisher,title", unique = true)
            })
    static class Book {
        @Id
        int id;

        @Column(unique = true)
        String isbn;

        String author;
        String title;
        String publisher;
        int publishYear;
    }

    @Entity(name = "InvalidDirection")
    @Table(
            name = "invalid_direction",
            indexes = {@Index(name = "idx_invalid_options", columnList = "publishYear sideways")})
    static class InvalidDirection {
        @Id
        int id;

        int publishYear;
    }

    @Entity(name = "InvalidOptions")
    @Table(
            name = "invalid_options",
            indexes = {@Index(name = "idx_invalid_options", columnList = "publishYear", options = "something")})
    static class InvalidOptions {
        @Id
        int id;

        int publishYear;
    }

    @Entity(name = "InvalidFormula")
    @Table(
            name = "invalid_formula",
            indexes = {@Index(name = "idx_invalid_options", columnList = "publishYear")})
    static class InvalidFormula {
        @Id
        int id;

        @Formula(value = "3*x")
        int publishYear;
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {
        @Test
        void testForbiddenOptions() {
            assertThatThrownBy(() -> inRegistry(InvalidOptions.class, session -> {}))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage("Index idx_invalid_options on invalid_options has options, which is not supported");
        }

        @Test
        void testForbiddenFormula() {
            assertThatThrownBy(() -> inRegistry(InvalidFormula.class, session -> {}))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage("Formula is not supported");
        }
    }
}

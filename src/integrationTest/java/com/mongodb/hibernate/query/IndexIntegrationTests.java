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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.hibernate.TestCommandListener;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import org.bson.BsonDocument;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

public class IndexIntegrationTests {

    @Test
    void testIndexCreated() {
        final var registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of(
                        "hibernate.hbm2ddl.auto",
                        "update",
                        "jakarta.persistence.schema-generation.database.action",
                        "drop-and-create",
                        "hibernate.hbm2ddl.halt_on_error",
                        "true"))
                .build();
        final var testCommandListener = registry.requireService(TestCommandListener.class);
        try (final var sessionFactory = new MetadataSources()
                .addAnnotatedClass(Book.class)
                .buildMetadata(registry)
                .buildSessionFactory()) {
            sessionFactory.openSession().close();
        }
        final var commands = testCommandListener.getStartedCommands().stream()
                .filter(command -> command.containsKey("createIndexes"))
                .toList();
        assertEquals(4, commands.size());
        assertThat(commands).allSatisfy(command -> assertThat(
                        command.getString("createIndexes").getValue())
                .isEqualTo("books"));
        assertThat(commands)
                .extracting(command -> command.getArray("indexes"))
                .allSatisfy(indexes -> assertThat(indexes).hasSize(1));
        // Note that the driver drops unique=false properties
        assertThat(commands)
                .flatExtracting(command -> command.getArray("indexes"))
                .contains(
                        BsonDocument.parse("{ name: \"idx_on_single_col\", key: {publishYear: 1}}"),
                        BsonDocument.parse("{ name: \"idx_on_multi_cols\", key: {publisher: 1, author: 1}}"),
                        BsonDocument.parse("{ name: \"uniq_idx_on_single_col\", key: {isbn: 1}, unique: true}"),
                        BsonDocument.parse(
                                "{ name: \"uniq_idx_on_multi_cols\", key: {publisher: 1, title: 1}, unique: true}"));
    }

    @Test
    void testForbiddenOptions() {
        final var registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of(
                        "hibernate.hbm2ddl.auto",
                        "update",
                        "jakarta.persistence.schema-generation.database.action",
                        "drop-and-create",
                        "hibernate.hbm2ddl.halt_on_error",
                        "true"))
                .build();
        assertThrows(FeatureNotSupportedException.class, () -> {
            try (final var sessionFactory = new MetadataSources()
                    .addAnnotatedClass(InvalidOptions.class)
                    .buildMetadata(registry)
                    .buildSessionFactory()) {
                sessionFactory.openSession().close();
            }
        });
    }

    @Entity(name = "Book")
    @Table(
            name = "books",
            uniqueConstraints = {
                @UniqueConstraint(
                        name = "uniq_idx_on_single_col",
                        columnNames = {"isbn"}),
            },
            indexes = {
                @Index(name = "idx_on_single_col", columnList = "publishYear"),
                @Index(name = "idx_on_multi_cols", columnList = "publisher,author"),
                @Index(name = "uniq_idx_on_multi_cols", columnList = "publisher,title", unique = true)
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

    @Entity(name = "InvalidOptions")
    @Table(
            name = "invalid_options",
            indexes = {@Index(name = "idx_invalid_options", columnList = "publishYear", options = "something")})
    static class InvalidOptions {
        @Id
        int id;

        int publishYear;
    }
}

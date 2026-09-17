/*
 * Copyright 2025-present MongoDB, Inc.
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

package com.mongodb.hibernate.boot;

import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.DEFAULT_CATALOG;
import static org.hibernate.cfg.AvailableSettings.DEFAULT_SCHEMA;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code @Table(schema=...)} folds into a dotted collection name in the single database. Verified at three levels: the
 * physical collection a document lands in ({@code @InjectMongoCollection}), the emitted MQL
 * ({@code assertSelectionQuery}), and boot-time rejection of unsupported mappings ({@link Unsupported}).
 */
@DomainModel(
        annotatedClasses = {
            SchemaQualificationIntegrationTests.Book.class,
            SchemaQualificationIntegrationTests.Article.class,
            SchemaQualificationIntegrationTests.Author.class,
            SchemaQualificationIntegrationTests.SharedOne.class,
            SchemaQualificationIntegrationTests.SharedTwo.class,
            SchemaQualificationIntegrationTests.Animal.class,
            SchemaQualificationIntegrationTests.Dog.class
        })
class SchemaQualificationIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection("library.books")
    private MongoCollection<BsonDocument> libraryBooks;

    @InjectMongoCollection("shared.things")
    private MongoCollection<BsonDocument> sharedThings;

    @InjectMongoCollection("zoo.animals")
    private MongoCollection<BsonDocument> zooAnimals;

    @BeforeEach
    void seed() {
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(new Book(1, "MongoDB"));
            var author = new Author(10, "Alice");
            session.persist(author);
            session.persist(new Article(20, "Aggregation", author));
            session.persist(new SharedOne(100, "one"));
            session.persist(new SharedTwo(200, "two"));
            session.persist(new Dog(30, "canis", "labrador"));
        });
        commandHistory.clear();
    }

    // ~~~ write path: schema folds into the physical collection name ~~~

    @Test
    void schemaFoldsIntoCollectionName() {
        assertThat(libraryBooks.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(1))
                        .append("title", new BsonString("MongoDB")));
    }

    @Test
    void tableSharingIsAllowed() {
        assertThat(sharedThings.find()).hasSize(2);
    }

    @Test
    void singleTableInheritanceFoldsAndPersists() {
        assertThat(zooAnimals.find()).hasSize(1);
        var loaded = getSessionFactoryScope()
                .fromTransaction(session -> session.createSelectionQuery("from Animal", Animal.class)
                        .getSingleResult());
        assertThat(loaded).isInstanceOf(Dog.class);
        assertThat(((Dog) loaded).breed).isEqualTo("labrador");
    }

    @Test
    void updateAndDelete() {
        getSessionFactoryScope().inTransaction(session -> session.find(Book.class, 1).title = "updated");
        assertThat(libraryBooks.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(1))
                        .append("title", new BsonString("updated")));

        getSessionFactoryScope().inTransaction(session -> session.remove(session.find(Book.class, 1)));
        assertThat(libraryBooks.find()).isEmpty();
    }

    // ~~~ read path: the emitted MQL targets the folded collection name ~~~

    @Test
    void queryTargetsFoldedCollection() {
        assertSelectionQuery(
                "from Book",
                Book.class,
                """
                {
                  "aggregate": "library.books",
                  "pipeline": [
                    {
                      "$project": {
                        "_id": true,
                        "title": true
                      }
                    }
                  ]
                }
                """,
                singletonList(new Book(1, "MongoDB")),
                Set.of("library.books"));
    }

    @Test
    void crossSchemaJoinTargetsFoldedCollections() {
        assertSelectionQuery(
                "from Article a join fetch a.author",
                Article.class,
                """
                {
                  "aggregate": "content.articles",
                  "pipeline": [
                    {
                      "$lookup": {
                        "as": "#a2_0",
                        "foreignField": "_id",
                        "from": "writing.authors",
                        "localField": "author_id"
                      }
                    },
                    {
                      "$unwind": "$#a2_0"
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a2_0#_id": "$#a2_0._id",
                        "a2_0#name": "$#a2_0.name",
                        "title": true
                      }
                    }
                  ]
                }
                """,
                results -> assertThat(results).singleElement().satisfies(a -> {
                    assertThat(a.title).isEqualTo("Aggregation");
                    assertThat(a.author.name).isEqualTo("Alice");
                }),
                Set.of("content.articles", "writing.authors"));
    }

    @Entity(name = "Book")
    @Table(schema = "library", name = "books")
    static class Book {
        @Id
        int id;

        String title;

        Book() {}

        Book(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    @Entity(name = "Article")
    @Table(schema = "content", name = "articles")
    static class Article {
        @Id
        int id;

        String title;

        @ManyToOne
        Author author;

        Article() {}

        Article(int id, String title, Author author) {
            this.id = id;
            this.title = title;
            this.author = author;
        }
    }

    @Entity(name = "Author")
    @Table(schema = "writing", name = "authors")
    static class Author {
        @Id
        int id;

        String name;

        Author() {}

        Author(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Entity(name = "SharedOne")
    @Table(schema = "shared", name = "things")
    static class SharedOne {
        @Id
        int id;

        String v;

        SharedOne() {}

        SharedOne(int id, String v) {
            this.id = id;
            this.v = v;
        }
    }

    @Entity(name = "SharedTwo")
    @Table(schema = "shared", name = "things")
    static class SharedTwo {
        @Id
        int id;

        String v;

        SharedTwo() {}

        SharedTwo(int id, String v) {
            this.id = id;
            this.v = v;
        }
    }

    @Entity(name = "Animal")
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    @Table(schema = "zoo", name = "animals")
    static class Animal {
        @Id
        int id;

        String species;
    }

    @Entity(name = "Dog")
    static class Dog extends Animal {
        String breed;

        Dog() {}

        Dog(int id, String species, String breed) {
            this.id = id;
            this.species = species;
            this.breed = breed;
        }
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {
        @Test
        void catalogRejectedAtBoot() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(WithCatalog.class)
                            .buildMetadata())
                    .hasMessageContaining("Catalog is not supported");
        }

        @Test
        void defaultCatalogRejectedAtBoot() {
            var url = new Configuration().getProperties().getProperty(JAKARTA_JDBC_URL);
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting(JAKARTA_JDBC_URL, url)
                    .applySetting(DEFAULT_CATALOG, "cat")
                    .build();
            try {
                assertThatThrownBy(() -> new MetadataSources(registry)
                                .addAnnotatedClass(NoQualifier.class)
                                .buildMetadata())
                        .hasMessageContaining("Catalog is not supported");
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }

        @Test
        void dottedDefaultSchemaRejectedAtBoot() {
            var url = new Configuration().getProperties().getProperty(JAKARTA_JDBC_URL);
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting(JAKARTA_JDBC_URL, url)
                    .applySetting(DEFAULT_SCHEMA, "a.b")
                    .build();
            try {
                assertThatThrownBy(() -> new MetadataSources(registry)
                                .addAnnotatedClass(NoQualifier.class)
                                .buildMetadata())
                        .hasMessageContaining("The character [.] in a schema name is not supported");
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }

        @Test
        void secondaryTableCatalogRejectedAtBoot() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(WithSecondaryCatalog.class)
                            .buildMetadata())
                    .hasMessageContaining("Catalog is not supported");
        }

        @Test
        void dottedTableNameRejectedAtBoot() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(DottedTableName.class)
                            .buildMetadata())
                    .hasMessageContaining("The character [.] in a table name is not supported");
        }

        @Test
        void dottedSchemaNameRejectedAtBoot() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(DottedSchemaName.class)
                            .buildMetadata())
                    .hasMessageContaining("The character [.] in a schema name is not supported");
        }

        /**
         * Backticks, double quotes and square brackets are Hibernate ORM quoting markers, stripped before the name
         * reaches the check, so the '.' is exposed. A single quote is not a quoting marker, so it stays in the name
         * alongside the '.'. Neither form is exempt.
         */
        @ParameterizedTest
        @MethodSource
        void quotingDoesNotExemptADottedTableName(Class<?> annotatedClass, String reportedName) {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(annotatedClass)
                            .buildMetadata())
                    .hasMessageContaining("The character [.] in a table name is not supported")
                    .hasMessageContaining("is present in [" + reportedName + "]");
        }

        private static Stream<Arguments> quotingDoesNotExemptADottedTableName() {
            return Stream.of(
                    arguments(BacktickQuotedTableName.class, "a.b"),
                    arguments(DoubleQuotedTableName.class, "a.b"),
                    arguments(BracketQuotedTableName.class, "a.b"),
                    arguments(SingleQuotedTableName.class, "'a.b'"));
        }

        @Entity(name = "BacktickQuotedTableName")
        @Table(name = "`a.b`")
        static class BacktickQuotedTableName {
            @Id
            int id;
        }

        @Entity(name = "DoubleQuotedTableName")
        @Table(name = "\"a.b\"")
        static class DoubleQuotedTableName {
            @Id
            int id;
        }

        @Entity(name = "BracketQuotedTableName")
        @Table(name = "[a.b]")
        static class BracketQuotedTableName {
            @Id
            int id;
        }

        @Entity(name = "SingleQuotedTableName")
        @Table(name = "'a.b'")
        static class SingleQuotedTableName {
            @Id
            int id;
        }

        @Entity(name = "WithCatalog")
        @Table(catalog = "cat", name = "widgets")
        static class WithCatalog {
            @Id
            int id;
        }

        @Entity(name = "NoQualifier")
        @Table(name = "widgets")
        static class NoQualifier {
            @Id
            int id;
        }

        @Entity(name = "WithSecondaryCatalog")
        @Table(name = "widgets")
        @SecondaryTable(name = "widget_details", catalog = "cat")
        static class WithSecondaryCatalog {
            @Id
            int id;
        }

        @Entity(name = "DottedTableName")
        @Table(name = "a.b")
        static class DottedTableName {
            @Id
            int id;
        }

        @Entity(name = "DottedSchemaName")
        @Table(schema = "a.b", name = "c")
        static class DottedSchemaName {
            @Id
            int id;
        }
    }
}

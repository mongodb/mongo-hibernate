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

package com.mongodb.hibernate;

import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.DEFAULT_CATALOG;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** POC: proves {@code @Table(schema=...)} folds into a dotted collection name in the single database. */
@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            SchemaQualificationIntegrationTests.SchemaQualified.class,
            SchemaQualificationIntegrationTests.DottedName.class,
            SchemaQualificationIntegrationTests.SchemaAndDottedName.class,
            SchemaQualificationIntegrationTests.Article.class,
            SchemaQualificationIntegrationTests.Author.class
        })
@ExtendWith(MongoExtension.class)
class SchemaQualificationIntegrationTests implements SessionFactoryScopeAware, MongoServiceRegistryProducer {

    @InjectMongoCollection("library.books")
    private static MongoCollection<BsonDocument> libraryBooks;

    @InjectMongoCollection("archive.entries")
    private static MongoCollection<BsonDocument> archiveEntries;

    @InjectMongoCollection("s.a.b")
    private static MongoCollection<BsonDocument> schemaAndDotted;

    @InjectMongoCollection("content.articles")
    private static MongoCollection<BsonDocument> contentArticles;

    @InjectMongoCollection("writing.authors")
    private static MongoCollection<BsonDocument> writingAuthors;

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Test
    void schemaQualifiedEntityFoldsIntoDottedCollection() {
        var book = new SchemaQualified();
        book.id = 1;
        book.title = "MongoDB";
        sessionFactoryScope.inTransaction(session -> session.persist(book));

        assertThat(libraryBooks.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(1))
                        .append("title", new BsonString("MongoDB")));

        // read path: HQL query reads from the folded collection
        var found = sessionFactoryScope.fromTransaction(
                session -> session.createSelectionQuery("from SchemaQualified", SchemaQualified.class)
                        .getSingleResult());
        assertThat(found.title).isEqualTo("MongoDB");
    }

    @Test
    void dottedNameIsNotSplit() {
        var e = new DottedName();
        e.id = 2;
        e.v = "x";
        sessionFactoryScope.inTransaction(session -> session.persist(e));

        assertThat(archiveEntries.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(2))
                        .append("v", new BsonString("x")));
    }

    @Test
    void schemaAndDottedName() {
        var e = new SchemaAndDottedName();
        e.id = 3;
        e.v = "y";
        sessionFactoryScope.inTransaction(session -> session.persist(e));

        assertThat(schemaAndDotted.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(3))
                        .append("v", new BsonString("y")));
    }

    @Test
    void crossSchemaJoin() {
        var author = new Author();
        author.id = 10;
        author.name = "Alice";
        var article = new Article();
        article.id = 20;
        article.title = "Aggregation";
        article.author = author;
        sessionFactoryScope.inTransaction(session -> {
            session.persist(author);
            session.persist(article);
        });

        // documents landed in their respective folded collections
        assertThat(writingAuthors.find()).hasSize(1);
        assertThat(contentArticles.find()).hasSize(1);

        // a join across two differently-schema'd entities resolves via a same-database $lookup
        var loaded = sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                        "from Article a join fetch a.author where a.id = 20", Article.class)
                .getSingleResult());
        assertThat(loaded.author.name).isEqualTo("Alice");
    }

    @Entity(name = "SchemaQualified")
    @Table(schema = "library", name = "books")
    static class SchemaQualified {
        @Id
        int id;

        String title;
    }

    @Entity(name = "DottedName")
    @Table(name = "archive.entries")
    static class DottedName {
        @Id
        int id;

        String v;
    }

    @Entity(name = "SchemaAndDottedName")
    @Table(schema = "s", name = "a.b")
    static class SchemaAndDottedName {
        @Id
        int id;

        String v;
    }

    @Entity(name = "Article")
    @Table(schema = "content", name = "articles")
    static class Article {
        @Id
        int id;

        String title;

        @ManyToOne
        Author author;
    }

    @Entity(name = "Author")
    @Table(schema = "writing", name = "authors")
    static class Author {
        @Id
        int id;

        String name;
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
        void foldCollisionRejectedAtBoot() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(SchemaAName.class)
                            .addAnnotatedClass(DottedAB.class)
                            .buildMetadata())
                    .hasMessageContaining("resolve to the same collection");
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

        // resolves to collection "a.b"
        @Entity(name = "SchemaAName")
        @Table(schema = "a", name = "b")
        static class SchemaAName {
            @Id
            int id;
        }

        // also resolves to collection "a.b", via a dotted name
        @Entity(name = "DottedAB")
        @Table(name = "a.b")
        static class DottedAB {
            @Id
            int id;
        }
    }
}

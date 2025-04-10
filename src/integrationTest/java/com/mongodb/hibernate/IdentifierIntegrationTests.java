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

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            IdentifierIntegrationTests.WithSpaceAndDotAndMixedCase.class,
            IdentifierIntegrationTests.InBackticks.class,
            IdentifierIntegrationTests.StartingWithBacktick.class,
            IdentifierIntegrationTests.EndingWithBacktick.class,
            IdentifierIntegrationTests.InDoubleQuotes.class,
            IdentifierIntegrationTests.StartingWithDoubleQuote.class,
            IdentifierIntegrationTests.EndingWithDoubleQuote.class
        })
@ExtendWith(MongoExtension.class)
class IdentifierIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection(WithSpaceAndDotAndMixedCase.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollectionWithSpaceAndDotAndMixedCase;

    @InjectMongoCollection(InBackticks.ACTUAL_COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollectionInBackticks;

    @InjectMongoCollection(StartingWithBacktick.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollectionStartingWithBacktick;

    @InjectMongoCollection(EndingWithBacktick.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollectionEndingWithBacktick;

    @InjectMongoCollection(InDoubleQuotes.ACTUAL_COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollectionInDoubleQuotes;

    @InjectMongoCollection(StartingWithDoubleQuote.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollectionStartingWithDoubleQuote;

    @InjectMongoCollection(EndingWithDoubleQuote.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollectionEndingWithDoubleQuote;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void withSpaceAndDotAndMixedCase() {
        var item = new WithSpaceAndDotAndMixedCase();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollectionWithSpaceAndDotAndMixedCase.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(item.id))
                        .append(WithSpaceAndDotAndMixedCase.FIELD_NAME, new BsonInt32(item.v)));
        sessionFactoryScope.inTransaction(session -> session.get(WithSpaceAndDotAndMixedCase.class, item.id));
    }

    @Test
    void inBackticks() {
        var item = new InBackticks();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollectionInBackticks.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(item.id))
                        .append(InBackticks.ACTUAL_FIELD_NAME, new BsonInt32(item.v)));
        sessionFactoryScope.inTransaction(session -> session.get(InBackticks.class, item.id));
    }

    @Test
    void startingWithBacktick() {
        var item = new StartingWithBacktick();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollectionStartingWithBacktick.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(item.id))
                        .append(StartingWithBacktick.FIELD_NAME, new BsonInt32(item.v)));
        sessionFactoryScope.inTransaction(session -> session.get(StartingWithBacktick.class, item.id));
    }

    @Test
    void endingWithBacktick() {
        var item = new EndingWithBacktick();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollectionEndingWithBacktick.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(item.id))
                        .append(EndingWithBacktick.FIELD_NAME, new BsonInt32(item.v)));
        sessionFactoryScope.inTransaction(session -> session.get(EndingWithBacktick.class, item.id));
    }

    @Test
    void inDoubleQuotes() {
        var item = new InDoubleQuotes();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollectionInDoubleQuotes.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(item.id))
                        .append(InDoubleQuotes.ACTUAL_FIELD_NAME, new BsonInt32(item.v)));
        sessionFactoryScope.inTransaction(session -> session.get(InDoubleQuotes.class, item.id));
    }

    @Test
    void startingWithDoubleQuote() {
        var item = new StartingWithDoubleQuote();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollectionStartingWithDoubleQuote.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(item.id))
                        .append(StartingWithDoubleQuote.FIELD_NAME, new BsonInt32(item.v)));
        sessionFactoryScope.inTransaction(session -> session.get(StartingWithDoubleQuote.class, item.id));
    }

    @Test
    void endingWithDoubleQuote() {
        var item = new EndingWithDoubleQuote();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollectionEndingWithDoubleQuote.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(item.id))
                        .append(EndingWithDoubleQuote.FIELD_NAME, new BsonInt32(item.v)));
        sessionFactoryScope.inTransaction(session -> session.get(EndingWithDoubleQuote.class, item.id));
    }

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Entity
    @Table(name = WithSpaceAndDotAndMixedCase.COLLECTION_NAME)
    static class WithSpaceAndDotAndMixedCase {
        static final String COLLECTION_NAME = "collection with space and .dot and Mixed Case";
        static final String FIELD_NAME = "field with space and Mixed Case";

        @Id
        int id;

        @Column(name = WithSpaceAndDotAndMixedCase.FIELD_NAME)
        int v;
    }

    @Entity
    @Table(name = InBackticks.COLLECTION_NAME)
    static class InBackticks {
        static final String COLLECTION_NAME = "`collection in backticks`";
        static final String FIELD_NAME = "`field in backticks`";
        static final String ACTUAL_COLLECTION_NAME = "collection in backticks";
        static final String ACTUAL_FIELD_NAME = "field in backticks";

        @Id
        int id;

        @Column(name = InBackticks.FIELD_NAME)
        int v;
    }

    @Entity
    @Table(name = StartingWithBacktick.COLLECTION_NAME)
    static class StartingWithBacktick {
        static final String COLLECTION_NAME = "`collection starting with backtick";
        static final String FIELD_NAME = "`field starting with backtick";

        @Id
        int id;

        @Column(name = StartingWithBacktick.FIELD_NAME)
        int v;
    }

    @Entity
    @Table(name = EndingWithBacktick.COLLECTION_NAME)
    static class EndingWithBacktick {
        static final String COLLECTION_NAME = "collection ending with backtick`";
        static final String FIELD_NAME = "field ending with backtick`";

        @Id
        int id;

        @Column(name = EndingWithBacktick.FIELD_NAME)
        int v;
    }

    @Entity
    @Table(name = InDoubleQuotes.COLLECTION_NAME)
    static class InDoubleQuotes {
        static final String COLLECTION_NAME = "\"collection in double quotes\"";
        static final String FIELD_NAME = "\"field in double quotes\"";
        static final String ACTUAL_COLLECTION_NAME = "collection in double quotes";
        static final String ACTUAL_FIELD_NAME = "field in double quotes";

        @Id
        int id;

        @Column(name = InDoubleQuotes.FIELD_NAME)
        int v;
    }

    @Entity
    @Table(name = StartingWithDoubleQuote.COLLECTION_NAME)
    static class StartingWithDoubleQuote {
        static final String COLLECTION_NAME = "\"collection starting with double quote";
        static final String FIELD_NAME = "\"field starting with double quote";

        @Id
        int id;

        @Column(name = StartingWithDoubleQuote.FIELD_NAME)
        int v;
    }

    @Entity
    @Table(name = EndingWithDoubleQuote.COLLECTION_NAME)
    static class EndingWithDoubleQuote {
        static final String COLLECTION_NAME = "collection ending with double quote\"";
        static final String FIELD_NAME = "field ending with double quote\"";

        @Id
        int id;

        @Column(name = EndingWithDoubleQuote.FIELD_NAME)
        int v;
    }
}

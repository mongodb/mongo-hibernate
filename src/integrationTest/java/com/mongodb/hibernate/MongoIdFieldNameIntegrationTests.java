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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            MongoIdFieldNameIntegrationTests.EntityWithoutIdColumnAnnotation.class,
            MongoIdFieldNameIntegrationTests.EntityWithIdColumnAnnotationWithoutNameElement.class,
            MongoIdFieldNameIntegrationTests.EntityWithIdColumnAnnotationWithValidNameElement.class
        })
class MongoIdFieldNameIntegrationTests implements SessionFactoryScopeAware {

    @AutoClose
    private MongoClient mongoClient;

    private MongoCollection<BsonDocument> collection;

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @BeforeAll
    void beforeAll() {
        var config = new MongoConfigurationBuilder(
                        sessionFactoryScope.getSessionFactory().getProperties())
                .build();
        mongoClient = MongoClients.create(config.mongoClientSettings());
        collection = mongoClient.getDatabase(config.databaseName()).getCollection("movies", BsonDocument.class);
    }

    @BeforeEach
    void beforeEach() {
        collection.drop();
    }

    @Test
    void testEntityWithoutIdColumnAnnotation(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithoutIdColumnAnnotation();
            movie.id = 1;
            session.persist(movie);
        });
    }

    @Test
    void testEntityWithIdColumnAnnotationWithoutNameElement(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithIdColumnAnnotationWithoutNameElement();
            movie.id = 1;
            session.persist(movie);
        });
    }

    @Test
    void testEntityWithIdColumnAnnotationWithNameElementIdentical(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithIdColumnAnnotationWithValidNameElement();
            movie.id = 1;
            session.persist(movie);
        });
    }

    @Entity
    @Table(name = "movies")
    static class EntityWithoutIdColumnAnnotation {
        @Id
        int id;
    }

    @Entity
    @Table(name = "movies")
    static class EntityWithIdColumnAnnotationWithoutNameElement {
        @Id
        @Column
        int id;
    }

    @Entity
    @Table(name = "movies")
    static class EntityWithIdColumnAnnotationWithValidNameElement {
        @Id
        @Column(name = "_id")
        int id;
    }
}

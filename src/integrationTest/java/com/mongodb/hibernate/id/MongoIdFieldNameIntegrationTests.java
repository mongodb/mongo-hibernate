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

package com.mongodb.hibernate.id;

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
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            MongoIdFieldNameIntegrationTests.EntityWithoutIdColumnAnnotation.class,
            MongoIdFieldNameIntegrationTests.EntityWithIdColumnAnnotationWithoutNameElement.class,
            MongoIdFieldNameIntegrationTests.EntityWithIdColumnAnnotationWithValidNameElement.class
        })
@ExtendWith(MongoExtension.class)
class MongoIdFieldNameIntegrationTests {

    @InjectMongoCollection("movies")
    private static MongoCollection<BsonDocument> collection;

    @Test
    void testEntityWithoutIdColumnAnnotation(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithoutIdColumnAnnotation();
            movie.id = 1;
            session.persist(movie);
        });
        assertCollectionContainsExactly(BsonDocument.parse("{_id: 1}"));
    }

    @Test
    void testEntityWithIdColumnAnnotationWithoutNameElement(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithIdColumnAnnotationWithoutNameElement();
            movie.id = 1;
            session.persist(movie);
        });
        assertCollectionContainsExactly(BsonDocument.parse("{_id: 1}"));
    }

    @Test
    void testEntityWithIdColumnAnnotationWithNameElementIdentical(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithIdColumnAnnotationWithValidNameElement();
            movie.id = 1;
            session.persist(movie);
        });
        assertCollectionContainsExactly(BsonDocument.parse("{_id: 1}"));
    }

    private static void assertCollectionContainsExactly(BsonDocument expectedDoc) {
        assertThat(collection.find()).containsExactly(expectedDoc);
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
        @Column(name = ID_FIELD_NAME)
        int id;
    }
}

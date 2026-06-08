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

package com.mongodb.hibernate.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// spring.mongodb.database must override the database embedded in spring.mongodb.uri, mirroring Spring's
// MongoDatabaseFactoryConfiguration. The entity must land in db_from_property, and db_from_uri must be untouched.
@SpringBootTest(
        classes = MongoHibernateSpringBootIntegrationTests.TestApplication.class,
        properties = {
            "spring.jpa.database-platform=MongoDB",
            "spring.mongodb.uri=mongodb://localhost/db_from_uri?directConnection=false",
            "spring.mongodb.database=db_from_property"
        })
class DatabaseNameOverrideIntegrationTests {

    @Autowired
    BookRepository bookRepository;

    @Autowired
    MongoClient mongoClient;

    @Autowired
    JpaTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        bookRepository.deleteAll();
    }

    @Test
    void springMongoDatabaseOverridesUriDatabase() {
        var id = new ObjectId();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> bookRepository.save(new TestBook(id, "Override")));

        // The document physically lands in the database from spring.mongodb.database (db_from_property),
        // not the one embedded in spring.mongodb.uri (db_from_uri). Checked with the raw driver against the
        // TestBook collection (entity name; @Id ObjectId maps to _id).
        assertThat(mongoClient
                        .getDatabase("db_from_property")
                        .getCollection("TestBook")
                        .countDocuments(Filters.eq("_id", id)))
                .isEqualTo(1L);
        assertThat(mongoClient
                        .getDatabase("db_from_uri")
                        .getCollection("TestBook")
                        .countDocuments())
                .isZero();
    }
}

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

package com.mongodb.hibernate.naming;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// An explicit spring.jpa.hibernate.naming.physical-strategy must be honored end to end: the stored
// field key becomes snake_case. This proves the default is overridable and not silently dropped.
@SpringBootTest(
        properties =
                "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
class MongoHibernateSnakeCaseNamingIntegrationTests {

    @Autowired
    NamingBookRepository repository;

    @Autowired
    JpaTransactionManager transactionManager;

    @Value("${spring.mongodb.uri}")
    String connectionString;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void explicitSnakeCaseStrategyIsHonoredInStoredDocument() {
        var id = new ObjectId();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> repository.save(new NamingBook(id, "Dune", 1965)));

        var document = NamingTestSupport.readStoredBook(connectionString, id);
        assertThat(document.containsKey("publish_year")).isTrue();
        assertThat(document.containsKey("publishYear")).isFalse();
    }
}

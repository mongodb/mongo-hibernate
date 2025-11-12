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

import static com.mongodb.hibernate.BasicCrudIntegrationTests.Item.COLLECTION_NAME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.hibernate.internal.boot.MongoAdditionalMappingContributor;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.InstantiationException;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Jpa(
        exportSchema = false,
        integrationSettings = {
            @Setting(name = AvailableSettings.DIALECT, value = "org.hibernate.dialect.PostgreSQLDialect"),
            @Setting(name = AvailableSettings.JAKARTA_JDBC_URL, value = "postgresql://"),
            @Setting(name = AvailableSettings.JAKARTA_JDBC_DRIVER, value = "org.postgresql.Driver"),
            @Setting(name = DriverManagerConnectionProviderImpl.INITIAL_SIZE, value = "0"),
        },
        annotatedClasses = PostgreSQLBootstrappingIntegrationTests.Item.class)
@ExtendWith(MongoExtension.class)
class PostgreSQLBootstrappingIntegrationTests {
    /**
     * Verify that {@link MongoAdditionalMappingContributor} skips its logic when bootstrapping is unrelated to the
     * MongoDB Extension for Hibernate ORM.
     */
    @Test
    void testMongoAdditionalMappingContributorIsSkipped(EntityManagerFactoryScope scope) {
        assertThatThrownBy(() -> scope.inTransaction(em -> em.persist(new Item(null))))
                .hasMessageNotContaining("does not support primary key spanning multiple columns")
                .isInstanceOf(InstantiationException.class)
                .hasMessageMatching("Could not instantiate entity .* due to: null");
    }

    @Entity
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        MultipleColumns id;

        Item(MultipleColumns id) {
            this.id = id;
        }
    }

    @Embeddable
    record MultipleColumns(int a, int b) {}
}

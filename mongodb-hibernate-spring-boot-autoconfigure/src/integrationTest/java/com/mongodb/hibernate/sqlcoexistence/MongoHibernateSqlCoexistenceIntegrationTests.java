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

package com.mongodb.hibernate.sqlcoexistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// A plain SQL JPA application with this module merely on its classpath. @ActiveProfiles("h2") loads
// application-h2.properties, which configures an in-memory H2 datasource and overrides the shared
// spring.jpa.database-platform=MongoDB with a SQL dialect; because OnMongoDatabasePlatformCondition no
// longer matches, our auto-configurations stay off. A successful boot plus a write that physically lands in
// H2 proves both that our Spring auto-configuration is inert AND that mongodb-hibernate's Hibernate
// ServiceContributor does not interfere with a non-MongoDB SessionFactory.
@SpringBootTest(classes = SqlCoexistenceApplication.class)
@ActiveProfiles("h2")
class MongoHibernateSqlCoexistenceIntegrationTests {

    @Autowired
    DataSource dataSource;

    @Autowired
    SqlWidgetRepository repository;

    @Test
    void sqlApplicationIsUnaffectedByMongoHibernateOnClasspath() throws SQLException {
        var saved = repository.save(new SqlWidget("gadget"));

        // Prove the write physically landed in the H2 SQL database — not MongoDB — by reading it back over a
        // raw JDBC connection on the autowired DataSource, bypassing JPA entirely. A repository round-trip
        // alone would pass regardless of which store backs the EntityManagerFactory; querying the SQL table
        // directly is what confirms Hibernate persisted through the SQL DataSource and that our Mongo
        // auto-configuration never engaged.
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("select name from sql_widget where id = ?")) {
            statement.setLong(1, saved.id);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("name")).isEqualTo("gadget");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }
}

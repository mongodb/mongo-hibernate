/*
 * Copyright 2024-present MongoDB, Inc.
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.mongodb.MongoClientSettings;
import com.mongodb.hibernate.dialect.MongoDialect;
import com.mongodb.hibernate.jdbc.MongoConnectionProvider;
import com.mongodb.hibernate.service.MongoClientCustomizer;
import java.util.Map;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoClientCustomizerTests {

    @Test
    void testMongoClientCustomizerTakeEffect(@Mock MongoClientCustomizer customizer) {
        buildSessionFactory(customizer);
        verify(customizer).customize(any(MongoClientSettings.Builder.class));
    }

    @Test
    void testMongoClientCustomizerThrowException(@Mock MongoClientCustomizer customizer) {
        doThrow(new NullPointerException()).when(customizer).customize(any(MongoClientSettings.Builder.class));
        assertThrows(ServiceException.class, () -> buildSessionFactory(customizer));
    }

    private void buildSessionFactory(MongoClientCustomizer mongoClientCustomizer) {
        var settings = Map.<String, Object>of(
                AvailableSettings.JAKARTA_JDBC_URL, "mongodb://localhost/test",
                AvailableSettings.DIALECT, MongoDialect.class.getName(),
                AvailableSettings.CONNECTION_PROVIDER, MongoConnectionProvider.class.getName());
        var standardServiceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .addService(MongoClientCustomizer.class, mongoClientCustomizer)
                .build();
        var sessionFactoryBuilder = new MetadataSources(standardServiceRegistry)
                .getMetadataBuilder()
                .build()
                .getSessionFactoryBuilder();
        try (var ignored = sessionFactoryBuilder.build()) {
            // no-op
        }
    }
}

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

package com.mongodb.hibernate.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.hibernate.boot.MetadataSources;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoClientCustomizerTests {

    @Test
    void testMongoClientCustomizerTakeEffect(
            @Mock MongoClientCustomizer customizer,
            @Mock MongoClientSettings clientSettings,
            @Mock MongoClient mongoClient) {

        // given
        var builder = Mockito.spy(MongoClientSettings.Builder.class);

        try (var mongoClientSettings = mockStatic(MongoClientSettings.class);
                var mongoClients = mockStatic(MongoClients.class)) {

            mongoClientSettings.when(MongoClientSettings::builder).thenReturn(builder);
            when(builder.build()).thenReturn(clientSettings);
            mongoClients
                    .when(() -> MongoClients.create(any(MongoClientSettings.class)))
                    .thenReturn(mongoClient);

            // when
            buildSessionFactory(customizer);

            // then
            verify(customizer).customize(same(builder)); // ensure customizing existing builder
            mongoClientSettings.verify(MongoClientSettings::builder, times(1));
        }
    }

    @Test
    void testMongoClientCustomizerThrowException(@Mock MongoClientCustomizer customizer) {
        doThrow(new NullPointerException()).when(customizer).customize(any(MongoClientSettings.Builder.class));
        assertThrows(ServiceException.class, () -> buildSessionFactory(customizer));
    }

    private void buildSessionFactory(MongoClientCustomizer mongoClientCustomizer) throws ServiceException {
        var cfg = new Configuration();
        var standardServiceRegistry = cfg.getStandardServiceRegistryBuilder()
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

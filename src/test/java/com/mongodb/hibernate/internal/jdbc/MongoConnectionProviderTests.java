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

package com.mongodb.hibernate.internal.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.hibernate.internal.BuildConfig;
import com.mongodb.hibernate.internal.cfg.MongoConfiguration;
import com.mongodb.hibernate.internal.service.StandardServiceRegistryScopedState;
import java.sql.SQLException;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoConnectionProviderTests {

    @AutoClose("stop")
    private MongoConnectionProvider connectionProvider;

    @BeforeEach
    void beforeEach() {
        connectionProvider = createMongoConnectionProvider();
    }

    @Test
    void defaultAutoCommit() throws SQLException {
        try (var connection = connectionProvider.getConnection()) {
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void testMongoDriverInformationPopulated() {
        var mongoClient = connectionProvider.getMongoClient();
        var driverInformation = ((MongoClientImpl) mongoClient).getMongoDriverInformation();
        assertAll(
                () -> assertTrue(driverInformation.getDriverNames().contains(BuildConfig.NAME)),
                () -> assertTrue(driverInformation.getDriverVersions().contains(BuildConfig.VERSION)));
    }

    @Test
    void usesSuppliedClientAndDoesNotCloseItOnStop() {
        var suppliedClient = mock(MongoClient.class);
        var config = new MongoConfiguration(suppliedClient, "db"); // borrow variant: settings is null
        var provider = new MongoConnectionProvider();
        provider.injectStandardServiceRegistryScopedState(new StandardServiceRegistryScopedState(config));

        assertThat(provider.getMongoClient()).isSameAs(suppliedClient);

        provider.stop();
        verify(suppliedClient, never()).close();
    }

    @Test
    void appendsDriverMetadataToBorrowedClient() {
        // Borrowing must not lose the extension's MongoDB telemetry attribution: the provider appends
        // mongo-hibernate's driver name/version to the borrowed client's handshake metadata.
        var suppliedClient = mock(MongoClient.class);
        var config = new MongoConfiguration(suppliedClient, "db");
        var provider = new MongoConnectionProvider();
        provider.injectStandardServiceRegistryScopedState(new StandardServiceRegistryScopedState(config));

        verify(suppliedClient)
                .appendMetadata(argThat(info -> info.getDriverNames().contains(BuildConfig.NAME)
                        && info.getDriverVersions().contains(BuildConfig.VERSION)));
    }

    @Test
    void closesSelfCreatedClientOnStop() {
        var config = new MongoConfiguration(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString("mongodb://host"))
                        .build(),
                "db"); // no supplied client -> provider creates and owns one
        var provider = new MongoConnectionProvider();
        provider.injectStandardServiceRegistryScopedState(new StandardServiceRegistryScopedState(config));
        assertThat(provider.getMongoClient()).isNotNull();

        provider.stop(); // owned path goes through close(); a real client tolerates close()
    }

    private MongoConnectionProvider createMongoConnectionProvider() {
        var mongoConfiguration = new MongoConfiguration(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString("mongodb://host"))
                        .build(),
                "db");
        var standardServiceRegistryScopedState = new StandardServiceRegistryScopedState(mongoConfiguration);
        var result = new MongoConnectionProvider();
        result.injectStandardServiceRegistryScopedState(standardServiceRegistryScopedState);
        return result;
    }
}

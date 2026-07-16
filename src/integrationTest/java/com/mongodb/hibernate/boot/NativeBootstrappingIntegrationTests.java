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

package com.mongodb.hibernate.boot;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoClient;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.bson.BsonDocument;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GeneratedColumn;
import org.hibernate.annotations.SourceType;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MongoExtension.class)
// Enables the mongod-global `failCommand` fail point; must run in the single-fork `integrationTestSerial` lane so it
// does not contend with parallel forks over that one shared server-side switch.
@Tag("serial")
class NativeBootstrappingIntegrationTests extends AbstractQueryIntegrationTests
        implements MongoServiceRegistryProducer {
    @InjectMongoClient
    private static MongoClient mongoClient;

    @Test
    void testCouldNotInstantiateDialectExceptionMessage() {
        assertThatThrownBy(() -> {
                    BsonDocument failPointCommand = BsonDocument.parse(
                            """
                            {
                                "configureFailPoint": "failCommand",
                                "mode": {
                                    "times": 1
                                },
                                "data": {
                                    "failCommands": ["buildInfo"],
                                    "errorCode": 1
                                }
                            }
                            """);
                    mongoClient.getDatabase("admin").runCommand(failPointCommand);
                    new MetadataSources().buildMetadata();
                })
                .hasRootCauseMessage(
                        "Could not instantiate [com.mongodb.hibernate.internal.dialect.MongoDialect], see the earlier exceptions to find out why");
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        @Entity(name = "ItemWithDbTimestamp")
        @Table(name = "ItemWithDbTimestamp")
        static class ItemWithDbTimestamp {
            @Id
            int id;

            String string;

            @Version
            @CurrentTimestamp(source = SourceType.DB)
            Instant version;
        }

        @Entity(name = "ItemWithGenerated")
        @Table(name = "ItemWithGenerated")
        static class ItemWithGenerated {
            @Id
            int id;

            String string;

            @Version
            @Generated(sql = "does not matter")
            Instant version;
        }

        @Entity(name = "ItemWithGeneratedColumn")
        @Table(name = "ItemWithGeneratedColumn")
        static class ItemWithGeneratedColumn {
            @Id
            int id;

            String string;

            @Version
            @GeneratedColumn(value = "does not matter")
            Instant version;
        }

        @Entity(name = "ItemWithGeneratedValue")
        @Table(name = "ItemWithGeneratedValue")
        static class ItemWithGeneratedValue {
            @Id
            @GeneratedValue
            int id;

            String string;
        }

        @Test
        void testForbidCurrentTimestampSourceDbAnnotation() {
            assertBootstrapThrows(() -> new MetadataSources()
                            .addAnnotatedClass(
                                    NativeBootstrappingIntegrationTests.Unsupported.ItemWithDbTimestamp.class)
                            .buildMetadata(new StandardServiceRegistryBuilder().build())
                            .buildSessionFactory())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage(
                            "Field version of class com.mongodb.hibernate.boot.NativeBootstrappingIntegrationTests.Unsupported.ItemWithDbTimestamp is not supported: Annotation @CurrentTimestamp(source=DB) is forbidden");
        }

        @Test
        void testForbidGeneratedAnnotation() {
            assertBootstrapThrows(() -> new MetadataSources()
                            .addAnnotatedClass(NativeBootstrappingIntegrationTests.Unsupported.ItemWithGenerated.class)
                            .buildMetadata(new StandardServiceRegistryBuilder().build())
                            .buildSessionFactory())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage(
                            "Field version of class com.mongodb.hibernate.boot.NativeBootstrappingIntegrationTests.Unsupported.ItemWithGenerated is not supported: Annotation org.hibernate.annotations.Generated is forbidden");
        }

        @Test
        void testForbidGeneratedValueAnnotation() {
            assertBootstrapThrows(() -> new MetadataSources()
                            .addAnnotatedClass(
                                    NativeBootstrappingIntegrationTests.Unsupported.ItemWithGeneratedValue.class)
                            .buildMetadata(new StandardServiceRegistryBuilder().build())
                            .buildSessionFactory())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage(
                            "Field id of class com.mongodb.hibernate.boot.NativeBootstrappingIntegrationTests.Unsupported.ItemWithGeneratedValue is not supported: Annotation jakarta.persistence.GeneratedValue is forbidden");
        }

        @Test
        void testForbidGeneratedColumnAnnotation() {
            assertBootstrapThrows(() -> new MetadataSources()
                            .addAnnotatedClass(
                                    NativeBootstrappingIntegrationTests.Unsupported.ItemWithGeneratedColumn.class)
                            .buildMetadata(new StandardServiceRegistryBuilder().build())
                            .buildSessionFactory())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage(
                            "Field version of class com.mongodb.hibernate.boot.NativeBootstrappingIntegrationTests.Unsupported.ItemWithGeneratedColumn is not supported: Annotation org.hibernate.annotations.GeneratedColumn is forbidden");
        }
    }
}

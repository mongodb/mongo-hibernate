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

package com.mongodb.hibernate.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bson.BsonDocument;
import org.hibernate.StaleObjectStateException;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.OptimisticLocking;
import org.hibernate.annotations.SourceType;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DomainModel(
        annotatedClasses = {
            OptimisticLockingIntegrationTests.ItemWithPrimitiveInt.class,
            OptimisticLockingIntegrationTests.ItemWithInteger.class,
            OptimisticLockingIntegrationTests.ItemWithPrimitiveLong.class,
            OptimisticLockingIntegrationTests.ItemWithLong.class,
            OptimisticLockingIntegrationTests.ItemWithInstant.class,
            OptimisticLockingIntegrationTests.ItemAllVersionless.class,
            OptimisticLockingIntegrationTests.ItemDirtyVersionless.class,
            OptimisticLockingIntegrationTests.ItemWithExcluded.class,
            OptimisticLockingIntegrationTests.ItemWithVmTimestamp.class
        })
class OptimisticLockingIntegrationTests extends AbstractQueryIntegrationTests implements MongoServiceRegistryProducer {

    @InjectMongoCollection("ItemWithInstant")
    private static MongoCollection<BsonDocument> itemWithInstantCollection;

    @InjectMongoCollection("ItemWithVmTimestamp")
    private static MongoCollection<BsonDocument> itemWithVmTimestampCollection;

    @InjectMongoCollection("ItemWithExcluded")
    private static MongoCollection<BsonDocument> itemWithExcludedCollection;

    @InjectMongoCollection("ItemWithPrimitiveLong")
    private static MongoCollection<BsonDocument> itemWithPrimitiveLongCollection;

    static Stream<Arguments> testUpdateNumericVersion() {
        return Stream.of(
                arguments(new ItemWithPrimitiveInt(1), "{ \"$numberInt\": \"0\" }", "{ \"$numberInt\": \"1\" }"),
                arguments(new ItemWithInteger(1), "{ \"$numberInt\": \"0\" }", "{ \"$numberInt\": \"1\" }"),
                arguments(new ItemWithPrimitiveLong(1), "{ \"$numberLong\": \"0\" }", "{ \"$numberLong\": \"1\" }"),
                arguments(new ItemWithLong(1), "{ \"$numberLong\": \"0\" }", "{ \"$numberLong\": \"1\" }"));
    }

    @ParameterizedTest
    @MethodSource
    void testUpdateNumericVersion(
            VersionedItem versionedItem, String expectedOldVersionMql, String expectedNewVersionMql) {
        getSessionFactoryScope().inTransaction(session -> {
            versionedItem.setString("str");
            session.persist(versionedItem);
        });

        getSessionFactoryScope().inTransaction(session -> {
            var loadedVersionedItem = session.find(versionedItem.getClass(), 1);
            getTestCommandListener().clear();
            loadedVersionedItem.setString("str_updated");
            session.flush();
            assertActualCommandsInOrder(BsonDocument.parse(
                    """
                    {
                      "update": "%s",
                      "updates": [
                        {
                          "q": { "$and": [ { "_id": { "$eq": 1 } }, { "version": { "$eq": %s } } ] },
                          "u": { "$set": { "string": "str_updated", "version": %s } },
                          "multi": true
                        }
                      ]
                    }
                    """
                            .formatted(
                                    loadedVersionedItem.getClass().getSimpleName(),
                                    expectedOldVersionMql,
                                    expectedNewVersionMql)));
        });
    }

    static Stream<Arguments> testUpdateNumericVersionConflictThrows() {
        return testUpdateNumericVersion();
    }

    @ParameterizedTest
    @MethodSource
    void testUpdateNumericVersionConflictThrows(
            VersionedItem item, String expectedOldVersionMql, String expectedNewVersionMql) {
        getSessionFactoryScope().inTransaction(session -> {
            item.setString("str");
            session.persist(item);
        });

        assertThatThrownBy(() -> getSessionFactoryScope().inSession(sessionA -> {
                    var itemA = sessionA.find(item.getClass(), 1);

                    getSessionFactoryScope().inTransaction(sessionB -> {
                        var fresh = sessionB.find(item.getClass(), 1);
                        fresh.setString("str_updated_b");
                    });

                    sessionA.beginTransaction();
                    getTestCommandListener().clear();
                    itemA.setString("str_updated_a");
                    sessionA.flush();
                    sessionA.getTransaction().commit();
                }))
                .isInstanceOfAny(OptimisticLockException.class);

        assertThat(getTestCommandListener().getStartedCommands().get(0))
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsAllEntriesOf(BsonDocument.parse(
                        """
                        {
                          "update": "%s",
                          "updates": [
                            {
                              "q": { "$and": [ { "_id": { "$eq": 1 } }, { "version": { "$eq": %s } } ] },
                              "u": { "$set": { "string": "str_updated_a", "version": %s } },
                              "multi": true
                            }
                          ]
                        }
                        """
                                .formatted(
                                        item.getClass().getSimpleName(),
                                        expectedOldVersionMql,
                                        expectedNewVersionMql)));
    }

    @Test
    void testUpdateWithInstantVersion() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemWithInstant(1);
            item.string = "str";
            session.persist(item);
        });
        var initialStoredMillis =
                itemWithInstantCollection.find().first().getDateTime("version").getValue();

        getSessionFactoryScope().inTransaction(session -> {
            var item = session.find(ItemWithInstant.class, 1);
            getTestCommandListener().clear();
            item.string = "str_updated";
            session.flush();
            var newVersionMillis = item.version.toEpochMilli();
            assertThat(newVersionMillis).isGreaterThanOrEqualTo(initialStoredMillis);
            assertActualCommandsInOrder(BsonDocument.parse(
                    """
                    {
                      "update": "ItemWithInstant",
                      "updates": [
                        {
                          "q": { "$and": [ { "_id": { "$eq": 1 } }, { "version": { "$eq": { "$date": { "$numberLong": "%d" } } } } ] },
                          "u": { "$set": { "string": "str_updated", "version": { "$date": { "$numberLong": "%d" } } } },
                          "multi": true
                        }
                      ]
                    }
                    """
                            .formatted(initialStoredMillis, newVersionMillis)));
        });
    }

    @Test
    void testUpdateWithCurrentTimestampVmVersion() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemWithVmTimestamp(1);
            item.string = "str";
            session.persist(item);
        });
        var initialStoredMillis = itemWithVmTimestampCollection
                .find()
                .first()
                .getDateTime("version")
                .getValue();

        getSessionFactoryScope().inTransaction(session -> {
            var item = session.find(ItemWithVmTimestamp.class, 1);
            getTestCommandListener().clear();
            item.string = "str_updated";
            session.flush();
            var newVersionMillis = item.version.toEpochMilli();
            assertThat(newVersionMillis).isGreaterThanOrEqualTo(initialStoredMillis);
            assertActualCommandsInOrder(BsonDocument.parse(
                    """
                    {
                      "update": "ItemWithVmTimestamp",
                      "updates": [
                        {
                          "q": { "$and": [ { "_id": { "$eq": 1 } }, { "version": { "$eq": { "$date": { "$numberLong": "%d" } } } } ] },
                          "u": { "$set": { "string": "str_updated", "version": { "$date": { "$numberLong": "%d" } } } },
                          "multi": true
                        }
                      ]
                    }
                    """
                            .formatted(initialStoredMillis, newVersionMillis)));
        });
    }

    static Stream<Arguments> testDeleteNumericVersion() {
        return Stream.of(
                arguments(new ItemWithPrimitiveInt(1), "{ \"$numberInt\": \"0\" }"),
                arguments(new ItemWithInteger(1), "{ \"$numberInt\": \"0\" }"),
                arguments(new ItemWithPrimitiveLong(1), "{ \"$numberLong\": \"0\" }"),
                arguments(new ItemWithLong(1), "{ \"$numberLong\": \"0\" }"));
    }

    @ParameterizedTest
    @MethodSource
    void testDeleteNumericVersion(VersionedItem versionedItem, String expectedOldVersionMql) {
        getSessionFactoryScope().inTransaction(session -> {
            versionedItem.setString("str");
            session.persist(versionedItem);
        });

        getSessionFactoryScope().inTransaction(session -> {
            var item = session.find(versionedItem.getClass(), 1);
            getTestCommandListener().clear();
            session.remove(item);
            session.flush();
            assertActualCommandsInOrder(BsonDocument.parse(
                    """
                    {
                      "delete": "%s",
                      "deletes": [
                        {
                          "q": { "$and": [ { "_id": { "$eq": 1 } }, { "version": { "$eq": %s } } ] },
                          "limit": 0
                        }
                      ]
                    }
                    """
                            .formatted(versionedItem.getClass().getSimpleName(), expectedOldVersionMql)));
        });
        getSessionFactoryScope().inTransaction(session -> assertThat(session.find(versionedItem.getClass(), 1))
                .isNull());
    }

    static Stream<Arguments> testDeleteNumericVersionConflictThrows() {
        return Stream.of(
                arguments(new ItemWithPrimitiveInt(1)),
                arguments(new ItemWithInteger(1)),
                arguments(new ItemWithPrimitiveLong(1)),
                arguments(new ItemWithLong(1)));
    }

    @ParameterizedTest
    @MethodSource
    void testDeleteNumericVersionConflictThrows(VersionedItem item) {
        getSessionFactoryScope().inTransaction(session -> {
            item.setString("str");
            session.persist(item);
        });
        var stale = getSessionFactoryScope().fromTransaction(session -> session.find(item.getClass(), 1));

        getSessionFactoryScope().inTransaction(session -> {
            var fresh = session.find(item.getClass(), 1);
            fresh.setString("str_updated_b");
        });

        assertThatThrownBy(() -> getSessionFactoryScope().inTransaction(session -> {
                    var merged = session.merge(stale);
                    session.remove(merged);
                }))
                .isInstanceOfAny(StaleObjectStateException.class, OptimisticLockException.class);
    }

    @Test
    void testForceVersionIncrement() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemWithPrimitiveLong(10);
            item.string = "str";
            session.persist(item);
        });

        getSessionFactoryScope().inTransaction(session -> {
            var item = session.find(ItemWithPrimitiveLong.class, 10);
            session.lock(item, org.hibernate.LockMode.OPTIMISTIC_FORCE_INCREMENT);
        });

        var stored = itemWithPrimitiveLongCollection
                .find(BsonDocument.parse("{\"_id\": {\"$eq\": 10}}"))
                .first();
        assertThat(stored).isNotNull();
        assertThat(stored.getInt64("version").getValue()).isEqualTo(1L);
    }

    @Test
    void testForceVersionIncrementConflictThrows() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemWithPrimitiveLong(11);
            item.string = "str";
            session.persist(item);
        });

        var stale = getSessionFactoryScope().fromTransaction(session -> session.find(ItemWithPrimitiveLong.class, 11));

        getSessionFactoryScope().inTransaction(sessionB -> {
            var itemB = sessionB.find(ItemWithPrimitiveLong.class, 11);
            sessionB.lock(itemB, org.hibernate.LockMode.OPTIMISTIC_FORCE_INCREMENT);
        });

        assertThatThrownBy(() -> getSessionFactoryScope().inTransaction(session -> {
                    var merged = session.merge(stale);
                    session.lock(merged, org.hibernate.LockMode.OPTIMISTIC_FORCE_INCREMENT);
                }))
                .isInstanceOfAny(StaleObjectStateException.class, OptimisticLockException.class);
    }

    @Test
    void testOptimisticLockingAllHappyPath() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemAllVersionless();
            item.id = 1;
            item.string = "str";
            item.primitiveInt = 1;
            session.persist(item);
        });

        getSessionFactoryScope().inTransaction(session -> {
            var item = session.find(ItemAllVersionless.class, 1);
            getTestCommandListener().clear();
            item.primitiveInt = 2;
            session.flush();
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {
                              "update": "ItemAllVersionless",
                              "updates": [
                                {
                                  "q": {
                                    "$and": [
                                      { "_id": { "$eq": 1 } },
                                      { "primitiveInt": { "$eq": 1 } },
                                      { "string": { "$eq": "str" } }
                                    ]
                                  },
                                  "u": { "$set": { "primitiveInt": 2 } },
                                  "multi": true
                                }
                              ]
                            }
                            """));
        });
    }

    @Test
    void testOptimisticLockingAllConflictThrows() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemAllVersionless();
            item.id = 1;
            item.string = "str";
            item.primitiveInt = 100;
            session.persist(item);
        });

        assertThatThrownBy(() -> getSessionFactoryScope().inSession(sessionA -> {
                    sessionA.beginTransaction();
                    var itemA = sessionA.find(ItemAllVersionless.class, 1);
                    sessionA.getTransaction().commit();

                    getSessionFactoryScope().inTransaction(sessionB -> {
                        var itemB = sessionB.find(ItemAllVersionless.class, 1);
                        itemB.primitiveInt = 200;
                    });

                    sessionA.beginTransaction();
                    itemA.primitiveInt = 300;
                    sessionA.flush();
                    sessionA.getTransaction().commit();
                }))
                .isInstanceOfAny(StaleObjectStateException.class, OptimisticLockException.class);
    }

    @Test
    void testOptimisticLockingDirty() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemDirtyVersionless();
            item.id = 1;
            item.string = "str";
            item.primitiveInt = 1;
            session.persist(item);
        });

        getSessionFactoryScope().inTransaction(session -> {
            var item = session.find(ItemDirtyVersionless.class, 1);
            getTestCommandListener().clear();
            item.primitiveInt = 2;
            session.flush();
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {
                              "update": "ItemDirtyVersionless",
                              "updates": [
                                {
                                  "q": {
                                    "$and": [
                                      { "_id": { "$eq": 1 } },
                                      { "primitiveInt": { "$eq": 1 } }
                                    ]
                                  },
                                  "u": { "$set": { "primitiveInt": 2 } },
                                  "multi": true
                                }
                              ]
                            }
                            """));
        });
    }

    @Test
    void testOptimisticLockingDirtyConflictThrows() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemDirtyVersionless();
            item.id = 2;
            item.string = "str";
            item.primitiveInt = 1;
            session.persist(item);
        });

        assertThatThrownBy(() -> getSessionFactoryScope().inSession(sessionA -> {
                    sessionA.beginTransaction();
                    var itemA = sessionA.find(ItemDirtyVersionless.class, 2);
                    sessionA.getTransaction().commit();

                    getSessionFactoryScope().inTransaction(sessionB -> {
                        var itemB = sessionB.find(ItemDirtyVersionless.class, 2);
                        itemB.primitiveInt = 2;
                    });

                    sessionA.beginTransaction();
                    itemA.primitiveInt = 2;
                    sessionA.flush();
                    sessionA.getTransaction().commit();
                }))
                .isInstanceOfAny(StaleObjectStateException.class, OptimisticLockException.class);
    }

    @Test
    void testOptimisticLockExcludedDoesNotBumpVersion() {
        getSessionFactoryScope().inTransaction(session -> {
            var item = new ItemWithExcluded();
            item.id = 1;
            item.string = "str";
            item.primitiveLong = 0;
            session.persist(item);
        });

        getSessionFactoryScope().inTransaction(session -> {
            var item = session.find(ItemWithExcluded.class, 1);
            getTestCommandListener().clear();
            item.primitiveLong = 42;
            session.flush();
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {
                              "update": "ItemWithExcluded",
                              "updates": [
                                {
                                  "q": { "$and": [ { "_id": { "$eq": 1 } }, { "version": { "$eq": { "$numberLong": "0" } } } ] },
                                  "u": { "$set": { "string": "str", "version": { "$numberLong": "0" }, "primitiveLong": { "$numberLong": "42" } } },
                                  "multi": true
                                }
                              ]
                            }
                            """));
        });

        var stored = itemWithExcludedCollection.find().first();
        assertThat(stored).isNotNull();
        assertThat(stored.getInt64("version").getValue()).isEqualTo(0L);
        assertThat(stored.getInt64("primitiveLong").getValue()).isEqualTo(42L);
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        @Test
        void testVersionWithCurrentTimestampSourceDbThrows() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithDbTimestamp.class)
                            .buildMetadata(new StandardServiceRegistryBuilder().build())
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage(null);
        }

        @Test
        void testSecondaryTableThrows() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithSecondaryTable.class)
                            .buildMetadata(new StandardServiceRegistryBuilder().build())
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage(
                            "TODO-HIBERNATE-181 https://jira.mongodb.org/browse/HIBERNATE-181 @SecondaryTable is not supported");
        }

        @Test
        void testJoinedInheritanceThrows() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithJoinedInheritanceBase.class)
                            .addAnnotatedClass(ItemWithJoinedInheritanceChild.class)
                            .buildMetadata(new StandardServiceRegistryBuilder().build())
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage(
                            "TODO-HIBERNATE-69 https://jira.mongodb.org/browse/HIBERNATE-69 JOINED inheritance is not supported");
        }

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

        @Entity(name = "ItemWithSecondaryTable")
        @Table(name = "ItemWithSecondaryTable")
        @SecondaryTable(name = "ItemWithSecondaryTableExt")
        static class ItemWithSecondaryTable {
            @Id
            int id;

            String string;

            @Column(table = "ItemWithSecondaryTableExt")
            String subtitle;
        }

        @Entity(name = "ItemWithJoinedInheritanceBase")
        @Inheritance(strategy = InheritanceType.JOINED)
        static class ItemWithJoinedInheritanceBase extends VersionedItem {
            @Id
            int id;
        }

        @Entity(name = "ItemWithJoinedInheritanceChild")
        static class ItemWithJoinedInheritanceChild extends ItemWithJoinedInheritanceBase {
            String string;
        }
    }

    @Entity(name = "ItemWithPrimitiveInt")
    static class ItemWithPrimitiveInt extends VersionedItem {
        @Version
        int version;

        ItemWithPrimitiveInt() {}

        ItemWithPrimitiveInt(int id) {
            super(id);
        }
    }

    @Entity(name = "ItemWithInteger")
    static class ItemWithInteger extends VersionedItem {
        @Version
        Integer version;

        ItemWithInteger() {}

        ItemWithInteger(int id) {
            super(id);
        }
    }

    @Entity(name = "ItemWithPrimitiveLong")
    static class ItemWithPrimitiveLong extends VersionedItem {
        @Version
        long version;

        ItemWithPrimitiveLong() {}

        ItemWithPrimitiveLong(int id) {
            super(id);
        }
    }

    @Entity(name = "ItemWithLong")
    static class ItemWithLong extends VersionedItem {
        @Version
        Long version;

        ItemWithLong() {}

        ItemWithLong(int id) {
            super(id);
        }
    }

    @Entity(name = "ItemWithInstant")
    static class ItemWithInstant extends VersionedItem {
        @Version
        Instant version;

        ItemWithInstant() {}

        ItemWithInstant(int id) {
            super(id);
        }
    }

    @Entity(name = "ItemWithVmTimestamp")
    static class ItemWithVmTimestamp extends VersionedItem {
        @Version
        @CurrentTimestamp(source = SourceType.VM)
        Instant version;

        ItemWithVmTimestamp() {}

        ItemWithVmTimestamp(int id) {
            super(id);
        }
    }

    @Entity(name = "ItemAllVersionless")
    @OptimisticLocking(type = OptimisticLockType.ALL)
    @DynamicUpdate
    static class ItemAllVersionless {
        @Id
        int id;

        String string;
        int primitiveInt;
    }

    @Entity(name = "ItemDirtyVersionless")
    @OptimisticLocking(type = OptimisticLockType.DIRTY)
    @DynamicUpdate
    static class ItemDirtyVersionless {
        @Id
        int id;

        String string;
        int primitiveInt;
    }

    @Entity(name = "ItemWithExcluded")
    static class ItemWithExcluded {
        @Id
        int id;

        String string;

        @OptimisticLock(excluded = true)
        long primitiveLong;

        @Version
        long version;
    }

    @MappedSuperclass
    abstract static class VersionedItem {
        @Id
        int id;

        String string;

        VersionedItem() {}

        VersionedItem(int id) {
            this.id = id;
        }

        void setString(String string) {
            this.string = string;
        }
    }
}

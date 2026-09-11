/*
 * Copyright 2026-present MongoDB, Inc.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import org.bson.BsonBinary;
import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.hibernate.annotations.Struct;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            UuidAsIdIntegrationTests.ItemGenerated.class,
            UuidAsIdIntegrationTests.ItemGeneratedValue.class,
            UuidAsIdIntegrationTests.ItemGeneratedWithStringId.class,
            UuidAsIdIntegrationTests.ItemAssigned.class,
            UuidAsIdIntegrationTests.ItemWithUuidList.class,
            UuidAsIdIntegrationTests.ItemWithStruct.class,
            UuidAsIdIntegrationTests.ItemWithConverted.class
        })
@ExtendWith(MongoExtension.class)
class UuidAsIdIntegrationTests implements SessionFactoryScopeAware, MongoServiceRegistryProducer {
    private static final String COLLECTION_NAME = "items";

    @InjectMongoCollection(COLLECTION_NAME)
    private MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Test
    void insert() {
        var item = new ItemGenerated();
        item.token = UUID.randomUUID();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertNotNull(item.id);
        var expectedId = new BsonBinary(BsonBinarySubType.UUID_STANDARD, uuidToStandardBytes(item.id));
        var expectedToken = new BsonBinary(BsonBinarySubType.UUID_STANDARD, uuidToStandardBytes(item.token));
        assertThat(mongoCollection.find())
                .containsExactly(new BsonDocument(ID_FIELD_NAME, expectedId).append("token", expectedToken));
    }

    @Test
    void query() {
        var item = new ItemGenerated();
        item.token = UUID.randomUUID();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.createQuery("from ItemGenerated where id = :id", ItemGenerated.class)
                        .setParameter("id", item.id)
                        .uniqueResult());
        assertEquals(item.id, loadedItem.id);
        assertEquals(item.token, loadedItem.token);
    }

    @Test
    void insertWithAssignedId() {
        var id = UUID.randomUUID();
        var item = new ItemAssigned();
        item.id = id;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var expectedId = new BsonBinary(BsonBinarySubType.UUID_STANDARD, uuidToStandardBytes(id));
        assertThat(mongoCollection.find()).containsExactly(new BsonDocument(ID_FIELD_NAME, expectedId));
    }

    @Test
    void queryByToken() {
        var token = UUID.randomUUID();
        var item = new ItemGenerated();
        item.token = token;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.createQuery("from ItemGenerated where token = :token", ItemGenerated.class)
                        .setParameter("token", token)
                        .uniqueResult());
        assertEquals(item.id, loadedItem.id);
        assertEquals(token, loadedItem.token);
    }

    @Test
    void insertWithUuidList() {
        var values = List.of(UUID.randomUUID(), UUID.randomUUID());
        var item = new ItemWithUuidList();
        item.id = 1;
        item.values = values;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.createQuery("from ItemWithUuidList where id = :id", ItemWithUuidList.class)
                        .setParameter("id", 1)
                        .uniqueResult());
        assertEquals(values, loadedItem.values);
    }

    @Test
    void insertWithUuidInStruct() {
        var item = new ItemWithStruct();
        item.id = 1;
        item.embedded = new UuidEmbedded();
        item.embedded.value = UUID.randomUUID();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.createQuery("from ItemWithStruct where id = :id", ItemWithStruct.class)
                        .setParameter("id", 1)
                        .uniqueResult());
        assertEquals(item.embedded.value, loadedItem.embedded.value);
    }

    @Test
    void insertWithConvertedUuid() {
        var item = new ItemWithConverted();
        item.id = 1;
        item.value = UUID.randomUUID();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.createQuery("from ItemWithConverted where id = :id", ItemWithConverted.class)
                        .setParameter("id", 1)
                        .uniqueResult());
        assertEquals(item.value, loadedItem.value);
    }

    @Test
    void updateToken() {
        var item = new ItemGenerated();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var token = UUID.randomUUID();
        sessionFactoryScope.inTransaction(
                session -> session.createMutationQuery("update ItemGenerated set token = :token where id = :id")
                        .setParameter("token", token)
                        .setParameter("id", item.id)
                        .executeUpdate());
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.createQuery("from ItemGenerated where id = :id", ItemGenerated.class)
                        .setParameter("id", item.id)
                        .uniqueResult());
        assertEquals(token, loadedItem.token);
    }

    @Test
    void selectTokenScalar() {
        var token = UUID.randomUUID();
        var item = new ItemGenerated();
        item.token = token;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedToken = sessionFactoryScope.fromTransaction(
                session -> session.createQuery("select i.token from ItemGenerated i where i.id = :id", UUID.class)
                        .setParameter("id", item.id)
                        .uniqueResult());
        assertEquals(token, loadedToken);
    }

    @Test
    void nullTokenRoundTrip() {
        var item = new ItemGenerated();
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.createQuery("from ItemGenerated where id = :id", ItemGenerated.class)
                        .setParameter("id", item.id)
                        .uniqueResult());
        assertNull(loadedItem.token);
    }

    @Test
    void preassignedIdIsRejected() {
        var item = new ItemGenerated();
        item.id = UUID.randomUUID();
        assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                .isInstanceOf(EntityExistsException.class)
                .hasMessageContaining("Detached entity passed to persist");
        assertThat(mongoCollection.find()).isEmpty();
    }

    @Nested
    class JpaSpelling implements MongoServiceRegistryProducer {
        @Test
        void insertWithStringId() {
            var item = new ItemGeneratedWithStringId();
            sessionFactoryScope.inTransaction(session -> session.persist(item));
            assertNotNull(item.id);
            assertThat(mongoCollection.find())
                    .containsExactly(new BsonDocument(ID_FIELD_NAME, new BsonString(item.id)));
        }

        @Test
        void insert() {
            var item = new ItemGeneratedValue();
            sessionFactoryScope.inTransaction(session -> session.persist(item));
            assertNotNull(item.id);
            var expectedId = new BsonBinary(BsonBinarySubType.UUID_STANDARD, uuidToStandardBytes(item.id));
            assertThat(mongoCollection.find()).containsExactly(new BsonDocument(ID_FIELD_NAME, expectedId));
        }
    }

    private static byte[] uuidToStandardBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    @Entity(name = "ItemGenerated")
    @Table(name = COLLECTION_NAME)
    static class ItemGenerated {
        @Id
        @UuidGenerator
        UUID id;

        UUID token;
    }

    @Entity(name = "ItemGeneratedValue")
    @Table(name = COLLECTION_NAME)
    static class ItemGeneratedValue {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        UUID id;
    }

    @Entity(name = "ItemGeneratedWithStringId")
    @Table(name = COLLECTION_NAME)
    static class ItemGeneratedWithStringId {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        String id;
    }

    @Entity(name = "ItemAssigned")
    @Table(name = COLLECTION_NAME)
    static class ItemAssigned {
        @Id
        UUID id;
    }

    @Entity(name = "ItemWithUuidList")
    @Table(name = COLLECTION_NAME)
    static class ItemWithUuidList {
        @Id
        int id;

        List<UUID> values;
    }

    @Entity(name = "ItemWithStruct")
    @Table(name = COLLECTION_NAME)
    static class ItemWithStruct {
        @Id
        int id;

        UuidEmbedded embedded;
    }

    @Entity(name = "ItemWithConverted")
    @Table(name = COLLECTION_NAME)
    static class ItemWithConverted {
        @Id
        int id;

        @Convert(converter = UuidToStringConverter.class)
        UUID value;
    }

    @Converter
    static class UuidToStringConverter implements AttributeConverter<UUID, String> {
        @Override
        public String convertToDatabaseColumn(UUID attribute) {
            return attribute == null ? null : attribute.toString();
        }

        @Override
        public UUID convertToEntityAttribute(String dbData) {
            return dbData == null ? null : UUID.fromString(dbData);
        }
    }

    @Embeddable
    @Struct(name = "UuidEmbedded")
    static class UuidEmbedded {
        UUID value;
    }
}

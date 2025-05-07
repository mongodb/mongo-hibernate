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

package com.mongodb.hibernate;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            BasicCrudIntegrationTests.Item.class,
            BasicCrudIntegrationTests.ItemDynamicallyUpdated.class,
        })
@ExtendWith(MongoExtension.class)
class BasicCrudIntegrationTests implements SessionFactoryScopeAware {

    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Nested
    class InsertTests {
        @Test
        void testSimpleEntityInsertion() {
            sessionFactoryScope.inTransaction(session -> session.persist(new Item(
                    1,
                    'c',
                    1,
                    Long.MAX_VALUE,
                    Double.MAX_VALUE,
                    true,
                    'c',
                    1,
                    Long.MAX_VALUE,
                    Double.MAX_VALUE,
                    true,
                    "str",
                    BigDecimal.valueOf(10.1),
                    new ObjectId("000000000000000000000001"))));
            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveChar: "c",
                        primitiveInt: 1,
                        primitiveLong: {$numberLong: "9223372036854775807"},
                        primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                        primitiveBoolean: true,
                        boxedChar: "c",
                        boxedInt: 1,
                        boxedLong: {$numberLong: "9223372036854775807"},
                        boxedDouble: {$numberDouble: "1.7976931348623157E308"},
                        boxedBoolean: true,
                        string: "str",
                        bigDecimal: {$numberDecimal: "10.1"},
                        objectId: {$oid: "000000000000000000000001"}
                    }
                    """);
        }

        @Test
        @Disabled("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 enable this test")
        void testEntityWithNullFieldValuesInsertion() {
            sessionFactoryScope.inTransaction(session -> session.persist(new Item(
                    1,
                    'c',
                    1,
                    Long.MAX_VALUE,
                    Double.MAX_VALUE,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));
            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveChar: "c",
                        primitiveInt: 1,
                        primitiveLong: {$numberLong: "9223372036854775807"},
                        primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                        primitiveBoolean: true,
                        boxedChar: null,
                        boxedInt: null,
                        boxedLong: null,
                        boxedDouble: null,
                        boxedBoolean: null,
                        string: null,
                        bigDecimal: null,
                        objectId: null
                    }
                    """);
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void testSimpleDeletion() {

            var id = 1;
            sessionFactoryScope.inTransaction(session -> session.persist(new Item(
                    id,
                    'c',
                    1,
                    Long.MAX_VALUE,
                    Double.MAX_VALUE,
                    true,
                    'c',
                    1,
                    Long.MAX_VALUE,
                    Double.MAX_VALUE,
                    true,
                    "str",
                    BigDecimal.valueOf(10.1),
                    new ObjectId("000000000000000000000001"))));
            assertThat(mongoCollection.find()).hasSize(1);

            sessionFactoryScope.inTransaction(session -> {
                var item = session.getReference(Item.class, id);
                session.remove(item);
            });

            assertThat(mongoCollection.find()).isEmpty();
        }
    }

    @Nested
    class UpdateTests {

        @Test
        void testSimpleUpdate() {
            sessionFactoryScope.inTransaction(session -> {
                var item = new Item(
                        1,
                        'c',
                        1,
                        Long.MAX_VALUE,
                        Double.MAX_VALUE,
                        true,
                        'c',
                        1,
                        Long.MAX_VALUE,
                        Double.MAX_VALUE,
                        true,
                        "str",
                        BigDecimal.valueOf(10.1),
                        new ObjectId("000000000000000000000001"));
                session.persist(item);
                session.flush();
                item.primitiveBoolean = false;
                item.boxedBoolean = false;
            });

            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveChar: "c",
                        primitiveInt: 1,
                        primitiveLong: {$numberLong: "9223372036854775807"},
                        primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                        primitiveBoolean: false,
                        boxedChar: "c",
                        boxedInt: 1,
                        boxedLong: {$numberLong: "9223372036854775807"},
                        boxedDouble: {$numberDouble: "1.7976931348623157E308"},
                        boxedBoolean: false,
                        string: "str",
                        bigDecimal: {$numberDecimal: "10.1"},
                        objectId: {$oid: "000000000000000000000001"}
                    }
                    """);
        }

        @Test
        @Disabled("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 enable this test")
        void testSimpleUpdateWithNullFieldValues() {
            sessionFactoryScope.inTransaction(session -> {
                var item = new Item(
                        1,
                        'c',
                        1,
                        Long.MAX_VALUE,
                        Double.MAX_VALUE,
                        true,
                        'c',
                        1,
                        Long.MAX_VALUE,
                        Double.MAX_VALUE,
                        true,
                        "str",
                        BigDecimal.valueOf(10.1),
                        new ObjectId("000000000000000000000001"));
                session.persist(item);
                session.flush();
                item.boxedChar = null;
                item.boxedInt = null;
                item.boxedLong = null;
                item.boxedDouble = null;
                item.boxedBoolean = null;
                item.string = null;
                item.bigDecimal = null;
                item.objectId = null;
            });

            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveChar: "c",
                        primitiveInt: 1,
                        primitiveLong: {$numberLong: "9223372036854775807"},
                        primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                        primitiveBoolean: true,
                        boxedChar: null,
                        boxedInt: null,
                        boxedLong: null,
                        boxedDouble: null,
                        boxedBoolean: null,
                        string: null,
                        bigDecimal: null,
                        objectId: null
                    }
                    """);
        }

        @Test
        void testDynamicUpdate() {
            sessionFactoryScope.inTransaction(session -> {
                var item = new ItemDynamicallyUpdated(1, true, true);
                session.persist(item);
                session.flush();
                item.primitiveBoolean = false;
                item.boxedBoolean = false;
            });

            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveBoolean: false,
                        boxedBoolean: false
                    }
                    """);
        }

        @Test
        @Disabled("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 enable this test")
        void testDynamicUpdateWithNullFieldValues() {
            sessionFactoryScope.inTransaction(session -> {
                var item = new ItemDynamicallyUpdated(1, false, true);
                session.persist(item);
                session.flush();
                item.boxedBoolean = null;
            });

            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveBoolean: false,
                        boxedBoolean: null
                    }
                    """);
        }
    }

    @Nested
    class SelectTests {

        @Test
        void testFindByPrimaryKey() {
            var item = new Item(
                    1,
                    'c',
                    1,
                    Long.MAX_VALUE,
                    Double.MAX_VALUE,
                    true,
                    'c',
                    1,
                    Long.MAX_VALUE,
                    Double.MAX_VALUE,
                    true,
                    "str",
                    BigDecimal.valueOf(10.1),
                    new ObjectId("000000000000000000000001"));
            sessionFactoryScope.inTransaction(session -> session.persist(item));

            var loadedItem = sessionFactoryScope.fromTransaction(session -> session.find(Item.class, item.id));
            assertEq(item, loadedItem);
        }

        @Test
        @Disabled("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 enable this test")
        void testFindByPrimaryKeyWithNullFieldValues() {
            var item = new Item(
                    1, 'c', 1, Long.MAX_VALUE, Double.MAX_VALUE, true, null, null, null, null, null, null, null, null);
            sessionFactoryScope.inTransaction(session -> session.persist(item));

            var loadedItem = sessionFactoryScope.fromTransaction(session -> session.find(Item.class, item.id));
            assertEq(item, loadedItem);
        }
    }

    private static void assertCollectionContainsExactly(String json) {
        assertThat(mongoCollection.find()).containsExactly(BsonDocument.parse(json));
    }

    @Entity
    @Table(name = "items")
    static class Item {
        @Id
        int id;

        char primitiveChar;
        int primitiveInt;
        long primitiveLong;
        double primitiveDouble;
        boolean primitiveBoolean;
        Character boxedChar;
        Integer boxedInt;
        Long boxedLong;
        Double boxedDouble;
        Boolean boxedBoolean;
        String string;
        BigDecimal bigDecimal;
        ObjectId objectId;

        Item() {}

        Item(
                int id,
                char primitiveChar,
                int primitiveInt,
                long primitiveLong,
                double primitiveDouble,
                boolean primitiveBoolean,
                Character boxedChar,
                Integer boxedInt,
                Long boxedLong,
                Double boxedDouble,
                Boolean boxedBoolean,
                String string,
                BigDecimal bigDecimal,
                ObjectId objectId) {
            this.id = id;
            this.primitiveChar = primitiveChar;
            this.primitiveInt = primitiveInt;
            this.primitiveLong = primitiveLong;
            this.primitiveDouble = primitiveDouble;
            this.primitiveBoolean = primitiveBoolean;
            this.boxedChar = boxedChar;
            this.boxedInt = boxedInt;
            this.boxedLong = boxedLong;
            this.boxedDouble = boxedDouble;
            this.boxedBoolean = boxedBoolean;
            this.string = string;
            this.bigDecimal = bigDecimal;
            this.objectId = objectId;
        }
    }

    @Entity
    @Table(name = "items")
    static class ItemDynamicallyUpdated {
        @Id
        int id;

        boolean primitiveBoolean;
        Boolean boxedBoolean;

        ItemDynamicallyUpdated() {}

        ItemDynamicallyUpdated(int id, boolean primitiveBoolean, Boolean boxedBoolean) {
            this.id = id;
            this.primitiveBoolean = primitiveBoolean;
            this.boxedBoolean = boxedBoolean;
        }
    }
}

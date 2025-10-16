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

package com.mongodb.hibernate.query.select;

import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.hibernate.BasicCrudIntegrationTests.Item.COLLECTION_NAME;
import static com.mongodb.hibernate.MongoTestAssertions.assertEq;
import static com.mongodb.hibernate.internal.MongoConstants.EXTENDED_JSON_WRITER_SETTINGS;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.WRAPPER_ARRAY_HANDLING;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.mongodb.client.model.Projections;
import com.mongodb.hibernate.ArrayAndCollectionIntegrationTests;
import com.mongodb.hibernate.ArrayAndCollectionIntegrationTests.ItemWithArrayAndCollectionValues;
import com.mongodb.hibernate.ArrayAndCollectionIntegrationTests.ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections;
import com.mongodb.hibernate.BasicCrudIntegrationTests;
import com.mongodb.hibernate.BasicCrudIntegrationTests.Item;
import com.mongodb.hibernate.embeddable.EmbeddableIntegrationTests;
import com.mongodb.hibernate.embeddable.StructAggregateEmbeddableIntegrationTests;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.ColumnResult;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.Table;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.hibernate.query.QueryProducer;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            Item.class,
            NativeQueryIntegrationTests.ItemWithFlattenedValue.class,
            NativeQueryIntegrationTests.ItemWithFlattenedValueHavingArraysAndCollections.class,
            NativeQueryIntegrationTests.ItemWithNestedValue.class,
            NativeQueryIntegrationTests.ItemWithNestedValueHavingArraysAndCollections.class,
            ItemWithArrayAndCollectionValues.class,
            ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections.class
        })
@ServiceRegistry(settings = {@Setting(name = WRAPPER_ARRAY_HANDLING, value = "allow")})
@ExtendWith(MongoExtension.class)
class NativeQueryIntegrationTests implements SessionFactoryScopeAware {
    private SessionFactoryScope sessionFactoryScope;
    private Item item;
    private ItemWithFlattenedValue itemWithFlattenedValue;
    private ItemWithFlattenedValueHavingArraysAndCollections itemWithFlattenedValueHavingArraysAndCollections;
    private ItemWithNestedValue itemWithNestedValue;
    private ItemWithNestedValueHavingArraysAndCollections itemWithNestedValueHavingArraysAndCollections;
    private ItemWithArrayAndCollectionValues itemWithArrayAndCollectionValues;
    private ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections
            itemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @BeforeEach
    void beforeEach() {
        item = new Item(
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
        itemWithFlattenedValue = new ItemWithFlattenedValue(
                2,
                new EmbeddableIntegrationTests.Plural(
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
                        new ObjectId("000000000000000000000001")));
        itemWithFlattenedValueHavingArraysAndCollections = new ItemWithFlattenedValueHavingArraysAndCollections(
                3,
                new EmbeddableIntegrationTests.ArraysAndCollections(
                        new byte[] {2, 3},
                        new char[] {'s', 't', 'r'},
                        new int[] {5},
                        new long[] {Long.MAX_VALUE, 6},
                        new double[] {Double.MAX_VALUE},
                        new boolean[] {true},
                        new Character[] {'s', null, 't', 'r'},
                        new Integer[] {null, 7},
                        new Long[] {8L, null},
                        new Double[] {9.1d, null},
                        new Boolean[] {true, null},
                        new String[] {null, "str"},
                        new BigDecimal[] {null, BigDecimal.valueOf(10.1)},
                        new ObjectId[] {new ObjectId("000000000000000000000001"), null},
                        new StructAggregateEmbeddableIntegrationTests.Single[] {
                            new StructAggregateEmbeddableIntegrationTests.Single(1), null
                        },
                        asList('s', 't', null, 'r'),
                        new HashSet<>(asList(null, 5)),
                        asList(Long.MAX_VALUE, null, 6L),
                        asList(null, Double.MAX_VALUE),
                        asList(null, true),
                        asList("str", null),
                        asList(BigDecimal.valueOf(10.1), null),
                        asList(null, new ObjectId("000000000000000000000001")),
                        asList(new StructAggregateEmbeddableIntegrationTests.Single(1), null)));
        itemWithNestedValue = new ItemWithNestedValue(
                4,
                new StructAggregateEmbeddableIntegrationTests.Plural(
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
                        new ObjectId("000000000000000000000001")));
        var arraysAndCollections = new StructAggregateEmbeddableIntegrationTests.ArraysAndCollections(
                new byte[] {2, 3},
                new char[] {'s', 't', 'r'},
                new int[] {5},
                new long[] {Long.MAX_VALUE, 6},
                new double[] {Double.MAX_VALUE},
                new boolean[] {true},
                new Character[] {'s', null, 't', 'r'},
                new Integer[] {null, 7},
                new Long[] {8L, null},
                new Double[] {9.1d, null},
                new Boolean[] {true, null},
                new String[] {null, "str"},
                new BigDecimal[] {null, BigDecimal.valueOf(10.1)},
                new ObjectId[] {new ObjectId("000000000000000000000001"), null},
                new StructAggregateEmbeddableIntegrationTests.Single[] {
                    new StructAggregateEmbeddableIntegrationTests.Single(1), null
                },
                asList('s', 't', null, 'r'),
                new HashSet<>(asList(null, 5)),
                asList(Long.MAX_VALUE, null, 6L),
                asList(null, Double.MAX_VALUE),
                asList(null, true),
                asList("str", null),
                asList(BigDecimal.valueOf(10.1), null),
                asList(null, new ObjectId("000000000000000000000001")),
                asList(new StructAggregateEmbeddableIntegrationTests.Single(1), null));
        itemWithNestedValueHavingArraysAndCollections =
                new ItemWithNestedValueHavingArraysAndCollections(5, arraysAndCollections);
        itemWithArrayAndCollectionValues = new ItemWithArrayAndCollectionValues(
                6,
                new byte[] {2, 3},
                new char[] {'s', 't', 'r'},
                new int[] {5},
                new long[] {Long.MAX_VALUE, 6},
                new double[] {Double.MAX_VALUE},
                new boolean[] {true},
                new Character[] {'s', null, 't', 'r'},
                new Integer[] {null, 7},
                new Long[] {8L, null},
                new Double[] {9.1d, null},
                new Boolean[] {true, null},
                new String[] {null, "str"},
                new BigDecimal[] {null, BigDecimal.valueOf(10.1)},
                new ObjectId[] {new ObjectId("000000000000000000000001"), null},
                new StructAggregateEmbeddableIntegrationTests.Single[] {
                    new StructAggregateEmbeddableIntegrationTests.Single(1), null
                },
                asList('s', 't', null, 'r'),
                new HashSet<>(asList(null, 5)),
                asList(Long.MAX_VALUE, null, 6L),
                asList(null, Double.MAX_VALUE),
                asList(null, true),
                asList("str", null),
                asList(BigDecimal.valueOf(10.1), null),
                asList(null, new ObjectId("000000000000000000000001")),
                asList(new StructAggregateEmbeddableIntegrationTests.Single(1), null));
        itemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections =
                new ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections(
                        7,
                        new StructAggregateEmbeddableIntegrationTests.ArraysAndCollections[] {arraysAndCollections},
                        List.of(arraysAndCollections));
        sessionFactoryScope.inTransaction(session -> {
            session.persist(item);
            session.persist(itemWithFlattenedValue);
            session.persist(itemWithFlattenedValueHavingArraysAndCollections);
            session.persist(itemWithNestedValue);
            session.persist(itemWithNestedValueHavingArraysAndCollections);
            session.persist(itemWithArrayAndCollectionValues);
            session.persist(itemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections);
        });
    }

    /**
     * See <a
     * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#sql-entity-query">Entity
     * queries</a>, {@link QueryProducer#createNativeQuery(String, Class)}.
     */
    @Test
    void testEntity() {
        sessionFactoryScope.inSession(session -> {
            assertAll(
                    () -> {
                        var mql = mql(COLLECTION_NAME, List.of(match(eq(item.id)), Item.projectAll()));
                        assertEq(
                                item, session.createNativeQuery(mql, Item.class).getSingleResult());
                    },
                    () -> {
                        var mql = mql(
                                COLLECTION_NAME,
                                List.of(match(eq(itemWithFlattenedValue.id)), ItemWithFlattenedValue.projectAll()));
                        assertEq(
                                itemWithFlattenedValue,
                                session.createNativeQuery(mql, ItemWithFlattenedValue.class)
                                        .getSingleResult());
                    },
                    () -> {
                        var mql = mql(
                                COLLECTION_NAME,
                                List.of(
                                        match(eq(itemWithFlattenedValueHavingArraysAndCollections.id)),
                                        ItemWithFlattenedValueHavingArraysAndCollections.projectAll()));
                        assertEq(
                                itemWithFlattenedValueHavingArraysAndCollections,
                                session.createNativeQuery(mql, ItemWithFlattenedValueHavingArraysAndCollections.class)
                                        .getSingleResult());
                    },
                    () -> {
                        var mql = mql(
                                COLLECTION_NAME,
                                List.of(
                                        match(eq(itemWithArrayAndCollectionValues.id)),
                                        ItemWithArrayAndCollectionValues.projectAll()));
                        assertEq(
                                itemWithArrayAndCollectionValues,
                                session.createNativeQuery(mql, ItemWithArrayAndCollectionValues.class)
                                        .getSingleResult());
                    },
                    () -> {
                        var mql = mql(
                                COLLECTION_NAME,
                                List.of(
                                        match(eq(
                                                itemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections
                                                        .id)),
                                        ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections
                                                .projectAll()));
                        assertEq(
                                itemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections,
                                session.createNativeQuery(
                                                mql,
                                                ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections
                                                        .class)
                                        .getSingleResult());
                    });
        });
    }

    /**
     * See <a
     * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#sql-scalar-query">Scalar
     * queries</a>, {@link QueryProducer#createNativeQuery(String, Class)}.
     */
    @Test
    void testScalar() {
        sessionFactoryScope.inSession(session -> assertAll(
                () -> {
                    var mql = mql(COLLECTION_NAME, List.of(match(eq(item.id)), project(include("objectId"))));
                    assertEq(
                            item.objectId,
                            session.createNativeQuery(mql, ObjectId.class).getSingleResult());
                },
                () -> {
                    var mql = mql(
                            COLLECTION_NAME,
                            List.of(
                                    match(eq(item.id)),
                                    exclude(Item.projectAll(), List.of("primitiveChar", "boxedChar"))));
                    assertEq(
                            new Object[] {
                                item.id,
                                item.primitiveInt,
                                item.primitiveLong,
                                item.primitiveDouble,
                                item.primitiveBoolean,
                                item.boxedInt,
                                item.boxedLong,
                                item.boxedDouble,
                                item.boxedBoolean,
                                item.string,
                                item.bigDecimal,
                                item.objectId
                            },
                            session.createNativeQuery(mql, Object[].class).getSingleResult());
                }));
    }

    /**
     * See <a
     * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#sql-dto-query">
     * Returning DTOs (Data Transfer Objects)</a>, {@link QueryProducer#createNativeQuery(String, Class)}.
     */
    @Nested
    class Dto {
        @Test
        void testBasicValues() {
            sessionFactoryScope.inSession(session -> {
                var mql = mql(COLLECTION_NAME, List.of(match(eq(item.id)), Item.projectAll()));
                assertEq(
                        item,
                        session.createNativeQuery(mql, Item.MAPPING_FOR_ITEM, Tuple.class)
                                .setTupleTransformer((tuple, aliases) -> new Item(
                                        (int) tuple[0],
                                        (char) tuple[1],
                                        (int) tuple[2],
                                        (long) tuple[3],
                                        (double) tuple[4],
                                        (boolean) tuple[5],
                                        (Character) tuple[6],
                                        (Integer) tuple[7],
                                        (Long) tuple[8],
                                        (Double) tuple[9],
                                        (Boolean) tuple[10],
                                        (String) tuple[11],
                                        (BigDecimal) tuple[12],
                                        (ObjectId) tuple[13]))
                                .getSingleResult());
            });
        }

        @Test
        void testEmbeddableValue() {
            sessionFactoryScope.inSession(session -> {
                var mql = mql(
                        COLLECTION_NAME,
                        List.of(match(eq(itemWithFlattenedValue.id)), ItemWithFlattenedValue.projectFlattened()));
                assertEq(
                        itemWithFlattenedValue.flattened,
                        session.createNativeQuery(mql, ItemWithFlattenedValue.MAPPING_FOR_FLATTENED_VALUE, Tuple.class)
                                .setTupleTransformer((tuple, aliases) -> new EmbeddableIntegrationTests.Plural(
                                        (char) tuple[0],
                                        (int) tuple[1],
                                        (long) tuple[2],
                                        (double) tuple[3],
                                        (boolean) tuple[4],
                                        (Character) tuple[5],
                                        (Integer) tuple[6],
                                        (Long) tuple[7],
                                        (Double) tuple[8],
                                        (Boolean) tuple[9],
                                        (String) tuple[10],
                                        (BigDecimal) tuple[11],
                                        (ObjectId) tuple[12]))
                                .getSingleResult());
            });
        }

        @Test
        void testEmbeddableValueHavingArraysAndCollections() {
            sessionFactoryScope.inSession(session -> {
                var mql = mql(
                        COLLECTION_NAME,
                        List.of(
                                match(eq(itemWithFlattenedValueHavingArraysAndCollections.id)),
                                ItemWithFlattenedValueHavingArraysAndCollections.projectFlattened()));
                assertEq(
                        itemWithFlattenedValueHavingArraysAndCollections.flattened,
                        session.createNativeQuery(
                                        mql,
                                        ItemWithFlattenedValueHavingArraysAndCollections.MAPPING_FOR_FLATTENED_VALUE,
                                        Tuple.class)
                                .setTupleTransformer(
                                        (tuple, aliases) -> new EmbeddableIntegrationTests.ArraysAndCollections(
                                                (byte[]) tuple[0],
                                                (char[]) tuple[1],
                                                (int[]) tuple[2],
                                                (long[]) tuple[3],
                                                (double[]) tuple[4],
                                                (boolean[]) tuple[5],
                                                (Character[]) tuple[6],
                                                (Integer[]) tuple[7],
                                                (Long[]) tuple[8],
                                                (Double[]) tuple[9],
                                                (Boolean[]) tuple[10],
                                                (String[]) tuple[11],
                                                (BigDecimal[]) tuple[12],
                                                (ObjectId[]) tuple[13],
                                                (StructAggregateEmbeddableIntegrationTests.Single[]) tuple[14],
                                                asList((Character[]) tuple[15]),
                                                new HashSet<>(asList((Integer[]) tuple[16])),
                                                asList((Long[]) tuple[17]),
                                                asList((Double[]) tuple[18]),
                                                asList((Boolean[]) tuple[19]),
                                                asList((String[]) tuple[20]),
                                                asList((BigDecimal[]) tuple[21]),
                                                asList((ObjectId[]) tuple[22]),
                                                asList((StructAggregateEmbeddableIntegrationTests.Single[]) tuple[23])))
                                .getSingleResult());
            });
        }

        @Test
        void testStructAggregateEmbeddableValue() {
            sessionFactoryScope.inSession(session -> {
                var mql = mql(
                        COLLECTION_NAME,
                        List.of(match(eq(itemWithNestedValue.id)), ItemWithNestedValue.projectNested()));
                assertEq(
                        itemWithNestedValue.nested,
                        session.createNativeQuery(mql, ItemWithNestedValue.MAPPING_FOR_NESTED_VALUE, Tuple.class)
                                .setTupleTransformer(
                                        (tuple, aliases) -> (StructAggregateEmbeddableIntegrationTests.Plural) tuple[0])
                                .getSingleResult());
            });
        }

        @Test
        void testStructAggregateEmbeddableValueHavingArraysAndCollections() {
            sessionFactoryScope.inSession(session -> {
                var mql = mql(
                        COLLECTION_NAME,
                        List.of(
                                match(eq(itemWithNestedValueHavingArraysAndCollections.id)),
                                ItemWithNestedValueHavingArraysAndCollections.projectNested()));
                assertEq(
                        itemWithNestedValueHavingArraysAndCollections.nested,
                        session.createNativeQuery(
                                        mql,
                                        ItemWithNestedValueHavingArraysAndCollections.MAPPING_FOR_NESTED_VALUE,
                                        Tuple.class)
                                .setTupleTransformer((tuple, aliases) ->
                                        (StructAggregateEmbeddableIntegrationTests.ArraysAndCollections) tuple[0])
                                .getSingleResult());
            });
        }

        @Test
        void testArrayAndCollectionValues() {
            sessionFactoryScope.inSession(session -> {
                var mql = mql(
                        COLLECTION_NAME,
                        List.of(
                                match(eq(itemWithArrayAndCollectionValues.id)),
                                ItemWithArrayAndCollectionValues.projectAll()));
                assertEq(
                        itemWithArrayAndCollectionValues,
                        session.createNativeQuery(mql, ItemWithArrayAndCollectionValues.MAPPING_FOR_ITEM, Tuple.class)
                                .setTupleTransformer((tuple, aliases) -> new ItemWithArrayAndCollectionValues(
                                        (int) tuple[0],
                                        (byte[]) tuple[1],
                                        (char[]) tuple[2],
                                        (int[]) tuple[3],
                                        (long[]) tuple[4],
                                        (double[]) tuple[5],
                                        (boolean[]) tuple[6],
                                        (Character[]) tuple[7],
                                        (Integer[]) tuple[8],
                                        (Long[]) tuple[9],
                                        (Double[]) tuple[10],
                                        (Boolean[]) tuple[11],
                                        (String[]) tuple[12],
                                        (BigDecimal[]) tuple[13],
                                        (ObjectId[]) tuple[14],
                                        (StructAggregateEmbeddableIntegrationTests.Single[]) tuple[15],
                                        asList((Character[]) tuple[16]),
                                        new HashSet<>(asList((Integer[]) tuple[17])),
                                        asList((Long[]) tuple[18]),
                                        asList((Double[]) tuple[19]),
                                        asList((Boolean[]) tuple[20]),
                                        asList((String[]) tuple[21]),
                                        asList((BigDecimal[]) tuple[22]),
                                        asList((ObjectId[]) tuple[23]),
                                        asList((StructAggregateEmbeddableIntegrationTests.Single[]) tuple[24])))
                                .getSingleResult());
            });
        }

        @Test
        void testArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections() {
            sessionFactoryScope.inSession(session -> {
                var mql = mql(
                        COLLECTION_NAME,
                        List.of(
                                match(eq(
                                        itemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections
                                                .id)),
                                ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections
                                        .projectAll()));
                assertEq(
                        itemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections,
                        session.createNativeQuery(
                                        mql,
                                        ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections
                                                .MAPPING_FOR_ITEM,
                                        Tuple.class)
                                .setTupleTransformer((tuple, aliases) ->
                                        new ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections(
                                                (int) tuple[0],
                                                (StructAggregateEmbeddableIntegrationTests.ArraysAndCollections[])
                                                        tuple[1],
                                                asList((StructAggregateEmbeddableIntegrationTests.ArraysAndCollections
                                                                [])
                                                        tuple[2])))
                                .getSingleResult());
            });
        }
    }

    @Nested
    class Unsupported {
        /**
         * We do not support this due to what seem to be a Hibernate ORM bug: <a
         * href="https://hibernate.atlassian.net/browse/HHH-19866">Entity native query incorrectly handles
         * AggregateSupport.preferSelectAggregateMapping that returns true</a>.
         *
         * @see #testEntity()
         */
        @Test
        void testEntityWithAggregateEmbeddableValue() {
            sessionFactoryScope.inSession(session -> {
                assertAll(
                        () -> assertThatThrownBy(() -> {
                                    var mql = mql(
                                            COLLECTION_NAME,
                                            List.of(
                                                    match(eq(itemWithNestedValue.id)),
                                                    ItemWithNestedValue.projectAll()));
                                    assertEq(
                                            itemWithFlattenedValue,
                                            session.createNativeQuery(mql, ItemWithNestedValue.class)
                                                    .getSingleResult());
                                })
                                .hasRootCauseInstanceOf(SQLException.class)
                                .hasMessageContaining("Not supported"),
                        () -> assertThatThrownBy(() -> {
                                    var mql = mql(
                                            COLLECTION_NAME,
                                            List.of(
                                                    match(eq(itemWithNestedValueHavingArraysAndCollections.id)),
                                                    ItemWithNestedValueHavingArraysAndCollections.projectAll()));
                                    assertEq(
                                            itemWithFlattenedValueHavingArraysAndCollections,
                                            session.createNativeQuery(
                                                            mql, ItemWithNestedValueHavingArraysAndCollections.class)
                                                    .getSingleResult());
                                })
                                .hasRootCauseInstanceOf(SQLException.class)
                                .hasMessageContaining("Not supported"));
            });
        }
    }

    private static String mql(String collectionName, Iterable<Bson> stages) {
        var pipeline = new BsonArray();
        stages.forEach(stage -> pipeline.add(stage.toBsonDocument()));
        return new BsonDocument("aggregate", new BsonString(collectionName))
                .append("pipeline", pipeline)
                .toJson(EXTENDED_JSON_WRITER_SETTINGS);
    }

    private static Bson exclude(Bson projectStage, Iterable<String> fieldNames) {
        var fieldsWithoutExclusions = projectStage.toBsonDocument().clone().getDocument("$project");
        var excludeId = false;
        for (var fieldName : fieldNames) {
            fieldsWithoutExclusions.remove(fieldName);
            if (fieldName.equals(ID_FIELD_NAME)) {
                excludeId = true;
            }
        }
        return excludeId
                ? project(fields(Projections.excludeId(), fieldsWithoutExclusions))
                : project(fieldsWithoutExclusions);
    }

    private static Bson excludeId(Bson projectStage) {
        return exclude(projectStage, singleton(ID_FIELD_NAME));
    }

    @Entity
    @Table(name = COLLECTION_NAME)
    @SqlResultSetMapping(
            name = ItemWithFlattenedValue.MAPPING_FOR_FLATTENED_VALUE,
            columns = {
                @ColumnResult(name = "primitiveChar", type = char.class),
                @ColumnResult(name = "primitiveInt"),
                @ColumnResult(name = "primitiveLong"),
                @ColumnResult(name = "primitiveDouble"),
                @ColumnResult(name = "primitiveBoolean"),
                @ColumnResult(name = "boxedChar", type = Character.class),
                @ColumnResult(name = "boxedInt"),
                @ColumnResult(name = "boxedLong"),
                @ColumnResult(name = "boxedDouble"),
                @ColumnResult(name = "boxedBoolean"),
                @ColumnResult(name = "string"),
                @ColumnResult(name = "bigDecimal"),
                @ColumnResult(name = "objectId")
            })
    static class ItemWithFlattenedValue {
        static final String MAPPING_FOR_FLATTENED_VALUE = "FlattenedValue";

        @Id
        int id;

        EmbeddableIntegrationTests.Plural flattened;

        ItemWithFlattenedValue() {}

        ItemWithFlattenedValue(int id, EmbeddableIntegrationTests.Plural flattened) {
            this.id = id;
            this.flattened = flattened;
        }

        static Bson projectAll() {
            return BasicCrudIntegrationTests.Item.projectAll();
        }

        static Bson projectFlattened() {
            return excludeId(projectAll());
        }
    }

    @Entity
    @Table(name = COLLECTION_NAME)
    @SqlResultSetMapping(
            name = ItemWithFlattenedValueHavingArraysAndCollections.MAPPING_FOR_FLATTENED_VALUE,
            columns = {
                @ColumnResult(name = "bytes", type = byte[].class),
                @ColumnResult(name = "chars", type = char[].class),
                @ColumnResult(name = "ints", type = int[].class),
                @ColumnResult(name = "longs", type = long[].class),
                @ColumnResult(name = "doubles", type = double[].class),
                @ColumnResult(name = "booleans", type = boolean[].class),
                @ColumnResult(name = "boxedChars", type = Character[].class),
                @ColumnResult(name = "boxedInts", type = Integer[].class),
                @ColumnResult(name = "boxedLongs", type = Long[].class),
                @ColumnResult(name = "boxedDoubles", type = Double[].class),
                @ColumnResult(name = "boxedBooleans", type = Boolean[].class),
                @ColumnResult(name = "strings", type = String[].class),
                @ColumnResult(name = "bigDecimals", type = BigDecimal[].class),
                @ColumnResult(name = "objectIds", type = ObjectId[].class),
                @ColumnResult(
                        name = "structAggregateEmbeddables",
                        type = StructAggregateEmbeddableIntegrationTests.Single[].class),
                @ColumnResult(name = "charsCollection", type = Character[].class),
                @ColumnResult(name = "intsCollection", type = Integer[].class),
                @ColumnResult(name = "longsCollection", type = Long[].class),
                @ColumnResult(name = "doublesCollection", type = Double[].class),
                @ColumnResult(name = "booleansCollection", type = Boolean[].class),
                @ColumnResult(name = "stringsCollection", type = String[].class),
                @ColumnResult(name = "bigDecimalsCollection", type = BigDecimal[].class),
                @ColumnResult(name = "objectIdsCollection", type = ObjectId[].class),
                @ColumnResult(
                        name = "structAggregateEmbeddablesCollection",
                        type = StructAggregateEmbeddableIntegrationTests.Single[].class)
            })
    static class ItemWithFlattenedValueHavingArraysAndCollections {
        static final String MAPPING_FOR_FLATTENED_VALUE = "FlattenedValueHavingArraysAndCollections";

        @Id
        int id;

        EmbeddableIntegrationTests.ArraysAndCollections flattened;

        ItemWithFlattenedValueHavingArraysAndCollections() {}

        ItemWithFlattenedValueHavingArraysAndCollections(
                int id, EmbeddableIntegrationTests.ArraysAndCollections flattened) {
            this.id = id;
            this.flattened = flattened;
        }

        static Bson projectAll() {
            return ArrayAndCollectionIntegrationTests.ItemWithArrayAndCollectionValues.projectAll();
        }

        static Bson projectFlattened() {
            return excludeId(projectAll());
        }
    }

    @Entity
    @Table(name = COLLECTION_NAME)
    @SqlResultSetMapping(
            name = ItemWithNestedValue.MAPPING_FOR_NESTED_VALUE,
            columns = {@ColumnResult(name = "nested", type = StructAggregateEmbeddableIntegrationTests.Plural.class)})
    static class ItemWithNestedValue {
        static final String MAPPING_FOR_NESTED_VALUE = "NestedValue";

        @Id
        int id;

        StructAggregateEmbeddableIntegrationTests.Plural nested;

        ItemWithNestedValue() {}

        ItemWithNestedValue(int id, StructAggregateEmbeddableIntegrationTests.Plural nested) {
            this.id = id;
            this.nested = nested;
        }

        static Bson projectAll() {
            return project(include(ID_FIELD_NAME, "nested"));
        }

        static Bson projectNested() {
            return excludeId(projectAll());
        }
    }

    @Entity
    @Table(name = COLLECTION_NAME)
    @SqlResultSetMapping(
            name = ItemWithNestedValueHavingArraysAndCollections.MAPPING_FOR_NESTED_VALUE,
            columns = {
                @ColumnResult(
                        name = "nested",
                        type = StructAggregateEmbeddableIntegrationTests.ArraysAndCollections.class)
            })
    static class ItemWithNestedValueHavingArraysAndCollections {
        static final String MAPPING_FOR_NESTED_VALUE = "NestedValueHavingArraysAndCollections";

        @Id
        int id;

        StructAggregateEmbeddableIntegrationTests.ArraysAndCollections nested;

        ItemWithNestedValueHavingArraysAndCollections() {}

        ItemWithNestedValueHavingArraysAndCollections(
                int id, StructAggregateEmbeddableIntegrationTests.ArraysAndCollections nested) {
            this.id = id;
            this.nested = nested;
        }

        static Bson projectAll() {
            return project(include(ID_FIELD_NAME, "nested"));
        }

        static Bson projectNested() {
            return excludeId(projectAll());
        }
    }
}

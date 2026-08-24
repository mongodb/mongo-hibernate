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

import static java.lang.String.format;

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.bson.types.ObjectId;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every inlined HQL literal is unwrapped through its {@code ValueBinder}, so each supported scalar type needs its own
 * assertion of the value the translator emits.
 */
@DomainModel(annotatedClasses = {QueryLiteralIntegrationTests.Item.class})
class QueryLiteralIntegrationTests extends AbstractQueryIntegrationTests {

    private static final String COLLECTION_NAME = "items";
    private static final String CONSTANTS = QueryLiteralConstants.class.getName();

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(matchingItem());
            session.persist(nonMatchingItem());
        });
    }

    private static Item matchingItem() {
        var item = new Item();
        item.id = 1;
        item.stringValue = QueryLiteralConstants.STRING;
        item.characterValue = QueryLiteralConstants.CHARACTER;
        item.intValue = QueryLiteralConstants.INT;
        item.longValue = QueryLiteralConstants.LONG;
        item.doubleValue = QueryLiteralConstants.DOUBLE;
        item.booleanValue = QueryLiteralConstants.BOOLEAN;
        item.bigDecimalValue = QueryLiteralConstants.BIG_DECIMAL;
        item.objectIdValue = QueryLiteralConstants.OBJECT_ID;
        item.instantValue = QueryLiteralConstants.INSTANT;
        return item;
    }

    private static Item nonMatchingItem() {
        var item = new Item();
        item.id = 2;
        item.stringValue = "Anna Karenina";
        item.characterValue = 'z';
        item.intValue = -1;
        item.longValue = -1L;
        item.doubleValue = -1.5;
        item.booleanValue = false;
        item.bigDecimalValue = new BigDecimal("-1.25");
        item.objectIdValue = new ObjectId("000000000000000000000002");
        item.instantValue = Instant.parse("1999-01-04T10:05:01Z");
        return item;
    }

    private static Stream<Arguments> literals() {
        return Stream.of(
                Arguments.of("stringValue", "STRING", """
                        "War & Peace\""""),
                Arguments.of("characterValue", "CHARACTER", """
                        "c\""""),
                Arguments.of("intValue", "INT", "42"),
                Arguments.of("longValue", "LONG", """
                        {"$numberLong": "43"}"""),
                Arguments.of("doubleValue", "DOUBLE", "4.5"),
                Arguments.of("booleanValue", "BOOLEAN", "true"),
                Arguments.of(
                        "bigDecimalValue", "BIG_DECIMAL", """
                        {"$numberDecimal": "4.25"}"""),
                Arguments.of(
                        "objectIdValue",
                        "OBJECT_ID",
                        """
                        {"$oid": "000000000000000000000001"}"""),
                Arguments.of(
                        "instantValue", "INSTANT", """
                        {"$date": "2025-01-04T10:05:01Z"}"""));
    }

    @ParameterizedTest(name = "testLiteralPredicate: {0}")
    @MethodSource("literals")
    void testLiteralPredicate(String attribute, String constant, String expectedBson) {
        assertSelectionQuery(
                format("select id from Item where %s = %s.%s", attribute, CONSTANTS, constant),
                Integer.class,
                format(
                        """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {"$match": {"%s": {"$eq": %s}}},
                            {"$project": {"_id": true}}
                          ]
                        }""",
                        attribute, expectedBson),
                List.of(1),
                Set.of(COLLECTION_NAME));
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        String stringValue;
        Character characterValue;
        int intValue;
        long longValue;
        double doubleValue;
        boolean booleanValue;
        BigDecimal bigDecimalValue;
        ObjectId objectIdValue;
        Instant instantValue;

        Item() {}
    }
}

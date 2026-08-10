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
            session.persist(new Item(1));
            session.persist(new Item(2));
        });
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

        /** Item 1 holds every constant's value; item 2 holds values none of the constants match. */
        Item(int id) {
            this.id = id;
            var matching = id == 1;
            this.stringValue = matching ? QueryLiteralConstants.STRING : "Anna Karenina";
            this.characterValue = matching ? QueryLiteralConstants.CHARACTER : 'z';
            this.intValue = matching ? QueryLiteralConstants.INT : -1;
            this.longValue = matching ? QueryLiteralConstants.LONG : -1L;
            this.doubleValue = matching ? QueryLiteralConstants.DOUBLE : -1.5;
            this.booleanValue = matching ? QueryLiteralConstants.BOOLEAN : !QueryLiteralConstants.BOOLEAN;
            this.bigDecimalValue = matching ? QueryLiteralConstants.BIG_DECIMAL : new BigDecimal("-1.25");
            this.objectIdValue = matching ? QueryLiteralConstants.OBJECT_ID : new ObjectId("000000000000000000000002");
            this.instantValue = matching ? QueryLiteralConstants.INSTANT : Instant.parse("1999-01-04T10:05:01Z");
        }
    }
}

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

package com.mongodb.hibernate.query.select;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DomainModel(annotatedClasses = {RegularExpressionIntegrationTests.ItemWithString.class})
class RegularExpressionIntegrationTests extends AbstractQueryIntegrationTests {

    private static final ItemWithString ITEM1 = new ItemWithString(1, "ab .");
    private static final ItemWithString ITEM2 = new ItemWithString(2, "AB .");
    private static final ItemWithString ITEM3 = new ItemWithString(3, "zz%");

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope()
                .inTransaction(session -> Stream.of(ITEM1, ITEM2, ITEM3).forEach(session::persist));
        getTestCommandListener().clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "i"})
    void testLikeMatchesNegated(String options) {
        runBasicNegatedTest(
                "from Item where str not %slike '%%ab%% .'".formatted(options),
                "/^.*ab.* \\.$/" + options,
                options.equals("i") ? List.of(ITEM3) : List.of(ITEM2, ITEM3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "i"})
    void testLikeMatches(String options) {
        runBasicTest(
                "from Item where str %slike '%%ab%% .'".formatted(options),
                "/^.*ab.* \\.$/" + options,
                options.equals("i") ? List.of(ITEM1, ITEM2) : List.of(ITEM1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "i"})
    void testLikeMatchSingle(String options) {
        runBasicTest(
                "from Item where str %slike 'a_ .'".formatted(options),
                "/^a. \\.$/" + options,
                options.equals("i") ? List.of(ITEM1, ITEM2) : List.of(ITEM1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "i"})
    void testLikeMatchSingleNegated(String options) {
        runBasicNegatedTest(
                "from Item where str not %slike 'a_ .'".formatted(options),
                "/^a. \\.$/" + options,
                options.equals("i") ? List.of(ITEM3) : List.of(ITEM2, ITEM3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "i"})
    void testLikeMatchesWithEscape(String options) {
        runBasicTest(
                "from Item where str %slike 'z%%!%%' escape '!'".formatted(options),
                "/^z.*%$/" + options,
                List.of(ITEM3));
    }

    private void runBasicTest(String query, String regex, List<ItemWithString> results) {
        assertSelectionQuery(
                query,
                ItemWithString.class,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$match": {
                        "str": {
                          "$regex": %ss
                        }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "str": true
                      }
                    }
                  ]
                }"""
                        .formatted(regex),
                results,
                Set.of(ItemWithString.COLLECTION_NAME));
    }

    private void runBasicNegatedTest(String query, String regex, List<ItemWithString> results) {
        assertSelectionQuery(
                query,
                ItemWithString.class,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$match": {
                        "$nor": [
                          { "str": { "$regex": %ss } }
                        ]
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "str": true
                      }
                    }
                  ]
                }"""
                        .formatted(regex),
                results,
                Set.of(ItemWithString.COLLECTION_NAME));
    }

    @Test
    void testLikeDoesNotTreatPlusAsRegexQuantifier() {
        var exactMatch = new ItemWithString(4, "a+b");
        var shouldNotMatch = new ItemWithString(5, "ab");
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(exactMatch);
            session.persist(shouldNotMatch);
        });

        var results = getSessionFactoryScope().fromTransaction(session -> session.createSelectionQuery(
                        "from Item where str like 'a+b'", ItemWithString.class)
                .getResultList());

        // '+' in a LIKE pattern is a literal character, not a regex quantifier: only the item whose
        // value is exactly "a+b" should match "a+b", not "ab".
        assertThat(results).containsExactly(exactMatch);
    }

    @Test
    void testLikeDoesNotMatchAcrossEmbeddedNewlines() {
        var itemWithEmbeddedNewline = new ItemWithString(4, "abc\ndef");
        getSessionFactoryScope().inTransaction(session -> session.persist(itemWithEmbeddedNewline));

        var results = getSessionFactoryScope().fromTransaction(session -> session.createSelectionQuery(
                        "from Item where str like 'def%'", ItemWithString.class)
                .getResultList());

        // The whole value "abc\ndef" does not start with "def", so LIKE 'def%' must not match it, even
        // though the value's second line does.
        assertThat(results).isEmpty();
    }

    @Test
    void testNonLiteralPattern() {
        assertSelectQueryFailure(
                "from Item where 'value' like str",
                ItemWithString.class,
                FeatureNotSupportedException.class,
                "Expression must be a literal String in pattern in LIKE, but other expression was found.");
        // We cannot test using an expression since the HQL parser rejects something like "from Item where str like
        // 'abcd' escape str"
    }

    @Test
    void testTrailingEscapingPattern() {
        assertSelectQueryFailure(
                "from Item where str like 'foo!' escape '!'",
                ItemWithString.class,
                IllegalArgumentException.class,
                "Escape character ! found at end of LIKE pattern: foo!");
    }

    @Test
    void testBadEscapingPattern() {
        assertSelectQueryFailure(
                "from Item where str like 'foo!$' escape '!'",
                ItemWithString.class,
                IllegalArgumentException.class,
                "Character $ is escaped by !, but only %%, _, and ! should be escaped");
    }

    @Entity(name = "Item")
    @Table(name = ItemWithString.COLLECTION_NAME)
    static final class ItemWithString {
        public static final String COLLECTION_NAME = "items";

        @Id
        int id;

        String str;

        public ItemWithString() {}

        public ItemWithString(int id, String str) {
            this.id = id;
            this.str = str;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ItemWithString that = (ItemWithString) o;
            return id == that.id && Objects.equals(str, that.str);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, str);
        }

        @Override
        public String toString() {
            return "ItemWithString{" + "id=" + id + ", str='" + str + '\'' + '}';
        }
    }
}

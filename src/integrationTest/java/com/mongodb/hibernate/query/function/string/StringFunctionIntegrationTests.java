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

package com.mongodb.hibernate.query.function.string;

import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.query.sqm.produce.function.FunctionArgumentException;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {StringFunctionIntegrationTests.Item.class})
@ExtendWith(MongoExtension.class)
public class StringFunctionIntegrationTests extends AbstractQueryIntegrationTests {
    private static final String COLLECTION_NAME = "items";
    private static final Item HELLO = new Item(1, " Hello ", "_Hello_");

    private void assertQueryResult(String hql, Object expected) {
        getSessionFactoryScope().inTransaction(session -> {
            var selectionQuery = session.createSelectionQuery(hql, Object[].class);
            var resultList = selectionQuery.getResultList();
            Assertions.assertEquals(1, resultList.size());
            Assertions.assertEquals(1, resultList.get(0).length);
            Assertions.assertEquals(expected, resultList.get(0)[0]);
        });
    }

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(HELLO);
        });
    }

    @Test
    void testCharacterLength() {
        assertQueryResult("select length(s) from Item", 7);
    }

    @Test
    void testConcat() {
        assertQueryResult("select s||s from Item", " Hello  Hello ");
    }

    @Test
    void testConcatCoerce() {
        assertQueryResult("select s||3 from Item", " Hello 3");
    }

    @Test
    void testConcatLiteral() {
        assertQueryResult("select concat(s, '!') from Item", " Hello !");
    }

    @Test
    void testLocate() {
        assertQueryResult("select locate('o', s) from Item", 6);
    }

    @Test
    void testLocateMissing() {
        assertQueryResult("select locate('z', s) from Item", 0);
    }

    @Test
    void testLocateOffset() {
        assertQueryResult("select locate('H', s, 1, 2) from Item", 2);
    }

    @Test
    void testLocateOffsetMissing() {
        assertQueryResult("select locate('o', s, 1, 2) from Item", 0);
    }

    @Test
    void testLower() {
        assertQueryResult("select lower(s) from Item", " hello ");
    }

    @Test
    void testLeftTrim() {
        assertQueryResult("select ltrim(s) from Item", "Hello ");
    }

    @Test
    void testLeftTrimChars() {
        assertQueryResult("select ltrim(u, '_') from Item", "Hello_");
    }

    @Test
    void testReplaceAll() {
        assertQueryResult("select replace_all(s, ' ', '_') from Item", "_Hello_");
    }

    @Test
    void testReplaceOne() {
        assertQueryResult("select replace_one(s, ' ', '_') from Item", "_Hello ");
    }

    @Test
    void testRightTrim() {
        assertQueryResult("select rtrim(s) from Item", " Hello");
    }

    @Test
    void testRightTrimChars() {
        assertQueryResult("select rtrim(u, '_') from Item", "_Hello");
    }

    @Test
    void testRightTrimWrongType() {
        assertSelectQueryFailure(
                "select rtrim(u, 3) from Item",
                String.class,
                FunctionArgumentException.class,
                "Parameter 2 of function 'rtrim()' has type 'STRING', but argument is of type 'java.lang.Integer' mapped to 'INTEGER'");
    }

    @Test
    void testSubstring() {
        assertQueryResult("select substring(s, 2) from Item", "Hello ");
    }

    @Test
    void testSubstringLen() {
        assertQueryResult("select substring(s, 3, 2) from Item", "el");
    }

    @Test
    void testTrim() {
        assertQueryResult("select trim(s) from Item", "Hello");
    }

    @Test
    void testTrimLeading() {
        assertQueryResult("select trim(leading from s) from Item", "Hello ");
    }

    @Test
    void testTrimTrailing() {
        assertQueryResult("select trim(trailing from s) from Item", " Hello");
    }

    @Test
    void testTrimBoth() {
        assertQueryResult("select trim(both from s) from Item", "Hello");
    }

    @Test
    void testTrimLeadingNonWS() {
        assertQueryResult("select trim(leading '_' from u) from Item", "Hello_");
    }

    @Test
    void testTrimTrailingNonWS() {
        assertQueryResult("select trim(trailing '_' from u) from Item", "_Hello");
    }

    @Test
    void testTrimBothNonWS() {
        assertQueryResult("select trim(both '_' from u) from Item", "Hello");
    }

    @Test
    void testUpper() {
        assertQueryResult("select upper(s) from Item", " HELLO ");
    }

    @Test
    void testSubstringWrongType() {
        assertSelectQueryFailure(
                "select substring(3, s) from Item",
                String.class,
                FunctionArgumentException.class,
                "Parameter 1 of function 'substring()' has type 'STRING', but argument is of type 'java.lang.Integer' mapped to 'INTEGER'");
    }

    @Test
    void testUpperWrongType() {
        assertSelectQueryFailure(
                "select upper(3) from Item",
                String.class,
                FunctionArgumentException.class,
                "Parameter 1 of function 'upper()' has type 'STRING', but argument is of type 'java.lang.Integer' mapped to 'INTEGER'");
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        String s;
        String u;

        Item() {}

        Item(int id, String s, String u) {
            this.id = id;
            this.s = s;
            this.u = u;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return id == item.id && Objects.equals(s, item.s) && Objects.equals(u, item.u);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, s, u);
        }

        @Override
        public String toString() {
            return "Item{" + "id=" + id + ", s='" + s + '\'' + ", u='" + u + '\'' + '}';
        }
    }
}

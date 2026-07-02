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

import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.MappedSuperclass;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.hibernate.annotations.DiscriminatorOptions;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            InheritanceIntegrationTests.MappedA.class,
            InheritanceIntegrationTests.SingleIntA.class,
            InheritanceIntegrationTests.SingleIntB.class,
            InheritanceIntegrationTests.SingleCharA.class,
            InheritanceIntegrationTests.SingleCharB.class,
            InheritanceIntegrationTests.SingleStrA.class,
            InheritanceIntegrationTests.SingleStrB.class
        })
public class InheritanceIntegrationTests extends AbstractQueryIntegrationTests {
    static final List<MappedA> MAPPED_A = List.of(new MappedA(1, "A", 65), new MappedA(2, "B", 66));
    static final SingleCharA SINGLE_CHAR_A = new SingleCharA(1, "A", 65);
    static final SingleCharB SINGLE_CHAR_B = new SingleCharB(2, "B", 66);
    static final SingleIntA SINGLE_INT_A = new SingleIntA(1, "A", 65);
    static final SingleIntB SINGLE_INT_B = new SingleIntB(2, "B", 66);
    static final SingleStrA SINGLE_STR_A = new SingleStrA(1, "A", 65);
    static final SingleStrB SINGLE_STR_B = new SingleStrB(2, "B", 66);

    @BeforeEach
    void beforeEach() {

        getSessionFactoryScope().inTransaction(session -> {
            MAPPED_A.forEach(session::persist);
            session.persist(SINGLE_CHAR_A);
            session.persist(SINGLE_CHAR_B);
            session.persist(SINGLE_INT_A);
            session.persist(SINGLE_INT_B);
            session.persist(SINGLE_STR_A);
            session.persist(SINGLE_STR_B);
        });
        getTestCommandListener().clear();
    }

    @Test
    void testReadMapped() {
        assertSelectionQuery(
                "from MappedA where uniqueOnA > 64",
                MappedA.class,
                """
                {
                  "aggregate": "MappedA",
                  "pipeline": [
                    {
                      "$match": {
                        "uniqueOnA": {"$gt": 64}
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "commonField": true,
                        "uniqueOnA": true
                      }
                    }
                  ]
                }""",
                MAPPED_A,
                Set.of("MappedA"));
    }

    @Nested
    class SingleInheritanceTests implements MongoServiceRegistryProducer {

        @Test
        void testReadSingleChar() {
            assertSelectionQuery(
                    "from SingleCharA",
                    SingleCharA.class,
                    """
                    {
                      "aggregate": "SingleChar",
                      "pipeline": [
                        {
                          "$match": {
                            "dtype": {"$eq": "a"}
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "commonField": true,
                            "uniqueOnA": true
                          }
                        }
                      ]
                    }""",
                    List.of(SINGLE_CHAR_A),
                    Set.of("SingleChar"));
        }

        @Test
        void testReadSingleCharSuper() {
            assertSelectionQuery(
                    "from SingleChar",
                    SingleCharSuper.class,
                    """
                    {
                      "aggregate": "SingleChar",
                      "pipeline": [
                        {
                          "$match": {
                            "dtype": {"$in": ["a", "b"]}
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "dtype": true,
                            "commonField": true,
                            "uniqueOnA": true,
                            "uniqueOnB": true
                          }
                        }
                      ]
                    }""",
                    List.of(SINGLE_CHAR_A, SINGLE_CHAR_B),
                    Set.of("SingleChar"));
        }

        @Test
        void testReadSingleStr() {
            assertSelectionQuery(
                    "from SingleStrA",
                    SingleStrA.class,
                    """
                    {
                      "aggregate": "SingleStr",
                      "pipeline": [
                        {
                          "$match": {
                            "dtype": {"$eq": "a'a"}
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "commonField": true,
                            "uniqueOnA": true
                          }
                        }
                      ]
                    }""",
                    List.of(SINGLE_STR_A),
                    Set.of("SingleStr"));
        }

        @Test
        void testReadSingleStrSuper() {
            assertSelectionQuery(
                    "from SingleStr",
                    SingleStrSuper.class,
                    """
                    {
                      "aggregate": "SingleStr",
                      "pipeline": [
                        {
                          "$match": {
                            "dtype": {"$in": ["a'a", "b"]}
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "dtype": true,
                            "commonField": true,
                            "uniqueOnA": true,
                            "uniqueOnB": true
                          }
                        }
                      ]
                    }""",
                    List.of(SINGLE_STR_A, SINGLE_STR_B),
                    Set.of("SingleStr"));
        }

        @Test
        void testReadSingleInt() {
            assertSelectionQuery(
                    "from SingleIntA",
                    SingleIntA.class,
                    """
                    {
                      "aggregate": "SingleInt",
                      "pipeline": [
                        {
                          "$match": {
                            "dtype": {"$eq": 1}
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "commonField": true,
                            "uniqueOnA": true
                          }
                        }
                      ]
                    }""",
                    List.of(SINGLE_INT_A),
                    Set.of("SingleInt"));
        }
    }

    @MappedSuperclass
    abstract static class MappedSuper {
        @Id
        int id;

        String commonField;

        public MappedSuper(int id, String commonField) {
            this.id = id;
            this.commonField = commonField;
        }

        public String getCommonField() {
            return commonField;
        }

        public void setCommonField(String commonField) {
            this.commonField = commonField;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    @Entity(name = "MappedA")
    static class MappedA extends MappedSuper {
        int uniqueOnA;

        public MappedA() {
            this(0, null, 0);
        }

        public MappedA(int id, String commonField, int uniqueOnA) {
            super(id, commonField);
            this.uniqueOnA = uniqueOnA;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            MappedA mappedA = (MappedA) o;
            return uniqueOnA == mappedA.uniqueOnA;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(uniqueOnA);
        }

        public int getUniqueOnA() {
            return uniqueOnA;
        }

        public void setUniqueOnA(int uniqueOnA) {
            this.uniqueOnA = uniqueOnA;
        }
    }

    @Entity(name = "SingleChar")
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    @DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.CHAR)
    @DiscriminatorOptions(force = true, insert = true)
    @DiscriminatorValue("z")
    abstract static class SingleCharSuper {
        @Id
        int id;

        String commonField;

        public SingleCharSuper(int id, String commonField) {
            this.id = id;
            this.commonField = commonField;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            SingleCharSuper that = (SingleCharSuper) o;
            return id == that.id && Objects.equals(commonField, that.commonField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, commonField);
        }

        public String getCommonField() {
            return commonField;
        }

        public void setCommonField(String commonField) {
            this.commonField = commonField;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    @Entity(name = "SingleCharA")
    @DiscriminatorValue("a")
    static class SingleCharA extends SingleCharSuper {
        int uniqueOnA;

        public SingleCharA() {
            this(0, null, 0);
        }

        public SingleCharA(int id, String commonField, int uniqueOnA) {
            super(id, commonField);
            this.uniqueOnA = uniqueOnA;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SingleCharA singleCharA = (SingleCharA) o;
            return uniqueOnA == singleCharA.uniqueOnA;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), uniqueOnA);
        }

        public int getUniqueOnA() {
            return uniqueOnA;
        }

        public void setUniqueOnA(int uniqueOnA) {
            this.uniqueOnA = uniqueOnA;
        }

        @Override
        public String toString() {
            return "SingleA{" + "uniqueOnA=" + uniqueOnA + ", id=" + id + ", commonField='" + commonField + '\'' + '}';
        }
    }

    @Entity(name = "SingleCharB")
    @DiscriminatorValue("b")
    static class SingleCharB extends SingleCharSuper {
        int uniqueOnB;

        public SingleCharB() {
            this(0, null, 0);
        }

        public SingleCharB(int id, String commonField, int uniqueOnB) {
            super(id, commonField);
            this.uniqueOnB = uniqueOnB;
        }

        public int getUniqueOnB() {
            return uniqueOnB;
        }

        public void setUniqueOnB(int uniqueOnB) {
            this.uniqueOnB = uniqueOnB;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SingleCharB singleCharB = (SingleCharB) o;
            return uniqueOnB == singleCharB.uniqueOnB;
        }

        @Override
        public String toString() {
            return "SingleB{" + "uniqueOnB=" + uniqueOnB + ", id=" + id + ", commonField='" + commonField + '\'' + '}';
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), uniqueOnB);
        }
    }

    @Entity(name = "SingleInt")
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    @DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.INTEGER)
    @DiscriminatorOptions(force = true, insert = true)
    abstract static class SingleIntSuper {
        @Id
        int id;

        String commonField;

        public SingleIntSuper(int id, String commonField) {
            this.id = id;
            this.commonField = commonField;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            SingleIntSuper that = (SingleIntSuper) o;
            return id == that.id && Objects.equals(commonField, that.commonField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, commonField);
        }

        public String getCommonField() {
            return commonField;
        }

        public void setCommonField(String commonField) {
            this.commonField = commonField;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    @Entity(name = "SingleIntA")
    @DiscriminatorValue("1")
    static class SingleIntA extends SingleIntSuper {
        int uniqueOnA;

        public SingleIntA() {
            this(0, null, 0);
        }

        public SingleIntA(int id, String commonField, int uniqueOnA) {
            super(id, commonField);
            this.uniqueOnA = uniqueOnA;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SingleIntA singleIntA = (SingleIntA) o;
            return uniqueOnA == singleIntA.uniqueOnA;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), uniqueOnA);
        }

        public int getUniqueOnA() {
            return uniqueOnA;
        }

        public void setUniqueOnA(int uniqueOnA) {
            this.uniqueOnA = uniqueOnA;
        }

        @Override
        public String toString() {
            return "SingleA{" + "uniqueOnA=" + uniqueOnA + ", id=" + id + ", commonField='" + commonField + '\'' + '}';
        }
    }

    @Entity(name = "SingleIntB")
    @DiscriminatorValue("2")
    static class SingleIntB extends SingleIntSuper {
        int uniqueOnB;

        public SingleIntB() {
            this(0, null, 0);
        }

        public SingleIntB(int id, String commonField, int uniqueOnB) {
            super(id, commonField);
            this.uniqueOnB = uniqueOnB;
        }

        public int getUniqueOnB() {
            return uniqueOnB;
        }

        public void setUniqueOnB(int uniqueOnB) {
            this.uniqueOnB = uniqueOnB;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SingleIntB singleIntB = (SingleIntB) o;
            return uniqueOnB == singleIntB.uniqueOnB;
        }

        @Override
        public String toString() {
            return "SingleB{" + "uniqueOnB=" + uniqueOnB + ", id=" + id + ", commonField='" + commonField + '\'' + '}';
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), uniqueOnB);
        }
    }

    @Entity(name = "SingleStr")
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    @DiscriminatorColumn(name = "dtype")
    @DiscriminatorOptions(force = true, insert = true)
    abstract static class SingleStrSuper {
        @Id
        int id;

        String commonField;

        public SingleStrSuper(int id, String commonField) {
            this.id = id;
            this.commonField = commonField;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            SingleStrSuper that = (SingleStrSuper) o;
            return id == that.id && Objects.equals(commonField, that.commonField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, commonField);
        }

        public String getCommonField() {
            return commonField;
        }

        public void setCommonField(String commonField) {
            this.commonField = commonField;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    @Entity(name = "SingleStrA")
    @DiscriminatorValue("a'a")
    static class SingleStrA extends SingleStrSuper {
        int uniqueOnA;

        public SingleStrA() {
            this(0, null, 0);
        }

        public SingleStrA(int id, String commonField, int uniqueOnA) {
            super(id, commonField);
            this.uniqueOnA = uniqueOnA;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SingleStrA singleStrA = (SingleStrA) o;
            return uniqueOnA == singleStrA.uniqueOnA;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), uniqueOnA);
        }

        public int getUniqueOnA() {
            return uniqueOnA;
        }

        public void setUniqueOnA(int uniqueOnA) {
            this.uniqueOnA = uniqueOnA;
        }

        @Override
        public String toString() {
            return "SingleA{" + "uniqueOnA=" + uniqueOnA + ", id=" + id + ", commonField='" + commonField + '\'' + '}';
        }
    }

    @Entity(name = "SingleStrB")
    @DiscriminatorValue("b")
    static class SingleStrB extends SingleStrSuper {
        int uniqueOnB;

        public SingleStrB() {
            this(0, null, 0);
        }

        public SingleStrB(int id, String commonField, int uniqueOnB) {
            super(id, commonField);
            this.uniqueOnB = uniqueOnB;
        }

        public int getUniqueOnB() {
            return uniqueOnB;
        }

        public void setUniqueOnB(int uniqueOnB) {
            this.uniqueOnB = uniqueOnB;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SingleStrB singleStrB = (SingleStrB) o;
            return uniqueOnB == singleStrB.uniqueOnB;
        }

        @Override
        public String toString() {
            return "SingleB{" + "uniqueOnB=" + uniqueOnB + ", id=" + id + ", commonField='" + commonField + '\'' + '}';
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), uniqueOnB);
        }
    }
}

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

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.List;
import java.util.Set;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = {InheritanceIntegrationTests.MappedA.class})
public class InheritanceIntegrationTests extends AbstractQueryIntegrationTests {
    static final List<MappedA> MAPPED_A = List.of(new MappedA(1, "A", 65), new MappedA(2, "B", 66));

    @BeforeEach
    void beforeEach() {

        getSessionFactoryScope().inTransaction(session -> {
            MAPPED_A.forEach(session::persist);
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
                        "uniqueOnA": true,
                      }
                    }
                  ]
                }""",
                MAPPED_A,
                Set.of("MappedA"));
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

        public int getUniqueOnA() {
            return uniqueOnA;
        }

        public void setUniqueOnA(int uniqueOnA) {
            this.uniqueOnA = uniqueOnA;
        }
    }
}

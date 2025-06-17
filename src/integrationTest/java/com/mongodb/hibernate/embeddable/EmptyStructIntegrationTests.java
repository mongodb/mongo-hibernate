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

package com.mongodb.hibernate.embeddable;

import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            EmptyStructIntegrationTests.StructHolder.class,
            EmptyStructIntegrationTests.EmptyStruct.class
        })
@ExtendWith(MongoExtension.class)
class EmptyStructIntegrationTests {

    @Test
    void test(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var holder = new StructHolder();
            holder.id = 1;
            holder.emptyStruct = new EmptyStruct();
            session.persist(holder);
        });
    }

    @Entity
    @Table(name = "collection")
    static class StructHolder {
        @Id
        int id;

        EmptyStruct emptyStruct;
    }

    @Embeddable
    @Struct(name = "EmptyStruct")
    static class EmptyStruct {
        // No fields
    }
}

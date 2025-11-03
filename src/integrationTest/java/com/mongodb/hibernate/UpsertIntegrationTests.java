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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {UpsertIntegrationTests.Item.class})
@ExtendWith(MongoExtension.class)
class UpsertIntegrationTests implements SessionFactoryScopeAware {
    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Test
    void unsupported() {
        var item = new Item(1, 1);
        sessionFactoryScope.inStatelessTransaction(session ->
                assertThatThrownBy(() -> session.upsert(item)).isInstanceOf(FeatureNotSupportedException.class));
    }

    @Entity
    @Table(name = Item.COLLECTION_NAME)
    static class Item {
        static final String COLLECTION_NAME = "items";

        @Id
        int id;
        /**
         * {@link org.hibernate.StatelessSession#upsert(Object)} results in no commands issued to the DBMS if {@link Id}
         * is the only persistent attribute, which must be a bug.
         */
        int v;

        Item() {}

        Item(int id, int v) {
            this.id = id;
            v = 1;
        }
    }
}

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

package com.mongodb.hibernate.type;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.MetadataSources;
import org.junit.jupiter.api.Test;

class JdbcTypeCodeIntegrationTests {

    @Test
    void entityWithJdbcTypeCodeOnBasicField() {
        assertNotSupported(ItemWithJdbcTypeCodeOnField.class);
    }

    @Test
    void entityWithJdbcTypeCodeInEmbeddable() {
        assertNotSupported(ItemWithEmbeddableContainingJdbcTypeCode.class);
    }

    /**
     * Test that @JdbcTypeCode is not supported even when it's nested within multiple levels of embeddables, as the
     * feature is not supported at all.
     */
    @Test
    void entityWithJdbcTypeCodeInNestedEmbeddable() {
        assertNotSupported(ItemWithNestedEmbeddableContainingJdbcTypeCode.class);
    }

    @Test
    void entityWithJdbcTypeCodeInSuperclass() {
        assertNotSupported(ItemInheritingJdbcTypeCode.class);
    }

    @Test
    void entityWithJdbcTypeCodeInEmbeddedId() {
        assertNotSupported(ItemWithJdbcTypeCodeInEmbeddedId.class);
    }

    @Test
    void entityWithJdbcTypeCodeInStructArray() {
        assertNotSupported(ItemWithJdbcTypeCodeInStructArray.class);
    }

    @Entity
    @Table(name = "items")
    static class ItemWithJdbcTypeCodeOnField {
        @Id
        int id;

        @JdbcTypeCode(Types.INTEGER)
        int value;
    }

    @Embeddable
    static class EmbeddableWithJdbcTypeCode {
        @JdbcTypeCode(Types.INTEGER)
        int value;
    }

    @Entity
    @Table(name = "items")
    static class ItemWithEmbeddableContainingJdbcTypeCode {
        @Id
        int id;

        @Embedded
        EmbeddableWithJdbcTypeCode embedded;
    }

    static class BaseEntityWithJdbcTypeCode {
        @JdbcTypeCode(Types.INTEGER)
        int value;
    }

    @Entity
    @Table(name = "items")
    static class ItemInheritingJdbcTypeCode extends BaseEntityWithJdbcTypeCode {
        @Id
        int id;
    }

    @Embeddable
    static class OuterEmbeddable {
        @Embedded
        EmbeddableWithJdbcTypeCode nested;
    }

    @Entity
    @Table(name = "items")
    static class ItemWithNestedEmbeddableContainingJdbcTypeCode {
        @Id
        int id;

        @Embedded
        OuterEmbeddable outer;
    }

    @Embeddable
    static class EmbeddableIdWithJdbcTypeCode {
        @JdbcTypeCode(Types.INTEGER)
        int key;
    }

    @Entity
    @Table(name = "items")
    static class ItemWithJdbcTypeCodeInEmbeddedId {
        @EmbeddedId
        EmbeddableIdWithJdbcTypeCode id;
    }

    @Embeddable
    @Struct(name = "structWithJdbcTypeCode")
    static class StructWithJdbcTypeCode {
        @JdbcTypeCode(Types.INTEGER)
        int value;
    }

    @Entity
    @Table(name = "items")
    static class ItemWithJdbcTypeCodeInStructArray {
        @Id
        int id;

        List<StructWithJdbcTypeCode> structs;
    }

    private static void assertNotSupported(Class<?> entityClass) {
        assertThatThrownBy(() ->
                        new MetadataSources().addAnnotatedClass(entityClass).buildMetadata())
                .isInstanceOf(FeatureNotSupportedException.class)
                .hasMessageContaining("@JdbcTypeCode is not supported");
    }
}

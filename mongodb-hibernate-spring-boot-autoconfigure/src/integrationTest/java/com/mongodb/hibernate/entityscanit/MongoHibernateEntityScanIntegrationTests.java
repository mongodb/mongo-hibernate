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

package com.mongodb.hibernate.entityscanit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.scannedentities.ScannedBook;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;

// classes = ScanTestApplication pins the context to this config. ScannedBook lives in a separate
// package (com.mongodb.hibernate.scannedentities), so it is found only because @EntityScan points
// at it — exercising the EntityScanPackages branch of MongoHibernateAutoConfiguration's package
// resolution (as opposed to the AutoConfigurationPackages fallback).
@SpringBootTest(classes = MongoHibernateEntityScanIntegrationTests.ScanTestApplication.class)
class MongoHibernateEntityScanIntegrationTests {

    @SpringBootApplication
    @EntityScan(basePackageClasses = ScannedBook.class)
    static class ScanTestApplication {}

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void entityFromEntityScanPackageIsMapped() {
        assertThat(entityManagerFactory.getMetamodel().getEntities())
                .anyMatch(type -> type.getJavaType().equals(ScannedBook.class));
    }
}

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

plugins {
    id("java-library")
    id("mongo-hibernate-publish")
}

repositories { mavenCentral() }

// No source code, just dependency aggregation following the Spring Boot starter convention:
// https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html#features.developing-auto-configuration.custom-starter

dependencies {
    api(project(":"))
    api(project(":mongodb-hibernate-spring-boot-autoconfigure"))
    api(platform(libs.spring.boot.bom))
    // Spring Data JPA + Hibernate ORM + spring-orm, but deliberately NOT spring-boot-starter-data-jpa: that
    // starter also drags in spring-boot-starter-jdbc -> HikariCP, a SQL connection pool a MongoDB-backed app
    // does not want. With a pooled DataSource on the classpath but no spring.datasource.url, Spring Boot's
    // DataSourceAutoConfiguration fails fast at startup ("'url' attribute is not specified"). Depending on the
    // granular spring-boot-data-jpa module (which still brings spring-boot-jdbc's auto-config classes, inert
    // without a pool) gives the full JPA stack without that pool. A user who genuinely wants SQL alongside
    // MongoDB adds HikariCP + a url themselves, and Spring behaves normally.
    //
    // No dependency on the base spring-boot-starter: a feature starter should not impose a logging backend or
    // other application foundation. The application's primary starter (spring-boot-starter-web, or plain
    // spring-boot-starter) provides that. spring-boot-data-jpa already brings the auto-configure engine.
    api("org.springframework.boot:spring-boot-data-jpa")
    api("org.springframework.boot:spring-boot-mongodb") // the MongoClient infrastructure the integration borrows
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.mongodb"
            artifactId = "mongodb-hibernate-spring-boot-starter"
            from(components["java"])
            pom {
                name = "Spring Boot Starter of MongoDB Extension for Hibernate ORM"
                description = "Spring Boot starter for the MongoDB Extension for Hibernate ORM"
                // url, licenses, developers, scm injected by mongo-hibernate-publish plugin
            }
        }
    }
}

// Regression guard for the deliberate omission of a SQL connection pool (see the dependencies block). If a
// pool implementation (HikariCP, etc.) or spring-boot-starter-jdbc ever reappears on the runtime classpath,
// a MongoDB-backed application would fail to start: Spring Boot's DataSourceAutoConfiguration would try to
// build a SQL DataSource and fail with "'url' attribute is not specified". Fail the build here instead.
val verifyNoSqlConnectionPool by
        tasks.registering {
            val componentIds =
                    configurations.named("runtimeClasspath").map { configuration ->
                        configuration.incoming.resolutionResult.allComponents.map { it.id.displayName }
                    }
            doLast {
                val poolMarkers =
                        listOf("HikariCP", "tomcat-jdbc", "commons-dbcp", "c3p0", "vibur", "oracle-ucp", "spring-boot-starter-jdbc")
                val offenders =
                        componentIds.get().filter { id -> poolMarkers.any { id.contains(it, ignoreCase = true) } }
                require(offenders.isEmpty()) {
                    "mongodb-hibernate-spring-boot-starter must not bring a SQL connection pool on its runtime " +
                            "classpath (Spring Boot's DataSourceAutoConfiguration would fail fast at startup): $offenders"
                }
            }
        }

tasks.named("check") { dependsOn(verifyNoSqlConnectionPool) }

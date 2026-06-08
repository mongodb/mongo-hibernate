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
    id("mongo-hibernate-java")
    id("mongo-hibernate-integration-test")
    id("mongo-hibernate-publish")
}

repositories { mavenCentral() }

// This module ships no module-info.java — like Spring Boot's own autoconfigure jars (which are
// automatic modules), it lives in a Spring ecosystem that is entirely automatic modules, so a full
// JPMS module descriptor would force an explicit module onto a sea of automatic ones for no benefit
// (module-path execution is not a tested mode). A stable Automatic-Module-Name is declared instead.
tasks.jar { manifest { attributes("Automatic-Module-Name" to "com.mongodb.hibernate.spring.boot.autoconfigure") } }

// 'optional' holds dependencies published in the POM at compile scope marked <optional>true</optional>:
// declared so a consumer depending on this artifact directly can resolve them, but not forced
// transitively. compileOnly/test extend it so the code compiles and is exercised against these
// libraries; it is deliberately NOT in 'implementation', so it isn't also published as a regular
// (non-optional) dependency.
val optional = configurations.create("optional") {
    isCanBeConsumed = false
    isCanBeResolved = false
}
listOf("compileOnly", "testImplementation").forEach { cfg ->
    configurations.named(cfg) { extendsFrom(optional) }
}
// Publish the 'optional' dependencies as Maven <optional>true</optional> via Gradle's native component
// variant mapping (replaces the need for pom.withXml).
(components["java"] as AdhocComponentWithVariants)
        .addVariantsFromConfiguration(optional) {
            mapToMavenScope("compile")
            mapToOptional()
        }

dependencies {
    // The Spring Boot BOM — published as a <dependencyManagement> import via api(platform) — manages
    // the versions of the version-less spring-*/jakarta.* entries below.
    api(platform(libs.spring.boot.bom))

    // Hard dependencies: required whenever the auto-configuration is active.
    api(project(":")) // mongodb-hibernate: required, and exposes the MongoConfigurationContributor SPI to consumers
    implementation(libs.spring.boot.autoconfigure) // @AutoConfiguration, @ConditionalOn*, AbstractRepositoryConfigurationSourceSupport
    implementation(libs.spring.boot.persistence) // EntityScanPackages — used unconditionally on the EntityManagerFactory path
    implementation("org.springframework.boot:spring-boot-jpa") // JpaProperties, EntityManagerFactoryBuilder, EntityManagerFactoryBuilderCustomizer
    implementation("org.springframework.boot:spring-boot-hibernate") // HibernateProperties, HibernateSettings, HibernatePropertiesCustomizer
    implementation("org.springframework:spring-orm") // LocalContainerEntityManagerFactoryBean, JpaTransactionManager
    implementation("jakarta.persistence:jakarta.persistence-api") // EntityManagerFactory

    compileOnly(libs.jspecify) // @NullMarked / @Nullable: compile-time annotations, not needed by consumers at runtime

    // Optional: only MongoJpaRepositoriesAutoConfiguration uses Spring Data JPA, and it is
    // @ConditionalOnClass(JpaRepository.class). Spring Boot filters that auto-configuration via
    // bytecode before loading it when Spring Data JPA is absent, so the dependency can back off cleanly.
    optional(libs.spring.data.jpa)

    // Optional: only the Open-Session-in-View inner configuration uses Spring MVC, and it is gated by
    // @ConditionalOnClass(WebMvcConfigurer) + @ConditionalOnWebApplication. A servlet web application
    // brings spring-webmvc transitively; non-web applications never load that configuration.
    optional("org.springframework:spring-webmvc")

    // Optional: the share bridge borrows the Spring-managed MongoClient and reads MongoConnectionDetails /
    // MongoProperties for the database name. Gated by @ConditionalOnClass(MongoConnectionDetails), so this is
    // inert when spring-boot-mongodb is absent.
    optional("org.springframework.boot:spring-boot-mongodb")

    // Most main dependencies (api/implementation/optional) are inherited by the test classpath, so
    // only genuinely test-only libraries are declared here.
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.bundles.test.common)
    testImplementation(libs.spring.boot.test.autoconfigure)
    testImplementation(libs.mockito.junit.jupiter)
    // DataSourceAutoConfiguration lives here; on the test classpath so the SQL-JPA precedence test
    // (mongoEntityManagerFactoryWinsOverSqlWhenDataSourcePresent) can register it and confirm Spring's
    // SQL JPA backs off behind the @Primary Mongo EntityManagerFactory.
    testImplementation("org.springframework.boot:spring-boot-jdbc")
    // Embedded SQL database for the SQL-JPA precedence test: DataSourceAutoConfiguration needs a driver
    // on the classpath to create a DataSource so HibernateJpaAutoConfiguration is exercised and can back off.
    testRuntimeOnly("com.h2database:h2")
    // WebApplicationContextRunner (OSIV test) needs a servlet API on the classpath to build a servlet
    // web context; spring-webmvc declares it as provided, so it is not pulled in transitively.
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    integrationTestImplementation(platform(libs.spring.boot.bom))
    integrationTestImplementation(libs.bundles.test.common)
    integrationTestImplementation(libs.spring.boot.starter.data.jpa)
    integrationTestImplementation("org.springframework.boot:spring-boot-mongodb") // provides the MongoClient to borrow
    integrationTestImplementation(libs.spring.boot.test.autoconfigure) // @SpringBootTest
    integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
    integrationTestRuntimeOnly("com.h2database:h2") // in-memory SQL DB for the coexistence test
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.mongodb"
            artifactId = "mongodb-hibernate-spring-boot-autoconfigure"
            from(components["java"])
            pom {
                name = "MongoDB Extension for Hibernate ORM — Spring Boot Auto-Configuration"
                description = "Spring Boot auto-configuration for the MongoDB Extension for Hibernate ORM"
                // url, licenses, developers, scm injected by mongo-hibernate-publish plugin
            }
        }
    }
}

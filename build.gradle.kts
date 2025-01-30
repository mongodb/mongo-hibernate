/*
 * Copyright 2024-present MongoDB, Inc.
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

import net.ltgt.gradle.errorprone.errorprone

version = "1.0.0-SNAPSHOT"

plugins {
    `java-library`
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.buildconfig)
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Integration Tests

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}
val integrationTestRuntimeOnly: Configuration by configurations.getting

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val integrationTest = task<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")

    useJUnitPlatform()

    testLogging {
        events("passed")
    }
}

tasks.check { dependsOn(integrationTest) }

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Static Analysis Tasks

spotless {
    java {
        importOrder()

        removeUnusedImports()

        palantirJavaFormat(libs.versions.palantir.get()).formatJavadoc(true)

        formatAnnotations()

        // need to add license header manually to package-info.java
        // due to the bug: https://github.com/diffplug/spotless/issues/532
        licenseHeaderFile("spotless.license.java") // contains '$YEAR' placeholder

        targetExclude("**/generated/**/*.java")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        option("NullAway:AnnotatedPackages", "com.mongodb.hibernate")
    }
}
tasks.compileJava {
    // The check defaults to a warning, bump it up to an error for the main sources
    options.errorprone.error("NullAway")
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Werror"))
}

tasks.compileTestJava {
    options.errorprone.isEnabled.set(false)
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Build Config

buildConfig {
    buildConfigField("NAME", provider { project.name })
    buildConfigField("VERSION", provider { "${project.version}" })
    packageName("com.mongodb.hibernate")
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Dependencies

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.logback.classic)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    integrationTestImplementation(libs.junit.jupiter)
    integrationTestImplementation(libs.logback.classic)
    integrationTestImplementation(libs.hibernate.testing) {
        exclude("org.apache.logging.log4j:log4j-core")
    }
    integrationTestRuntimeOnly(libs.junit.platform.launcher)

    errorprone(libs.nullaway)
    api(libs.jspecify)

    errorprone(libs.google.errorprone.core)

    implementation(libs.hibernate.core)
    implementation(libs.mongo.java.driver.sync)
    implementation(libs.sl4j.api)
}


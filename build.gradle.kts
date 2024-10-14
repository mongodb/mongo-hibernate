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

plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    errorprone(libs.nullaway)
    errorprone(libs.google.errorprone.core)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Static Analysis Tasks

spotless {
    java {
        // note: you can use an empty string for all the imports you didn't specify explicitly, '|' to join group without blank line, and '\\#` prefix for static imports
        importOrder("java|javax", "", "\\#")

        removeUnusedImports()

        palantirJavaFormat(libs.versions.palantir.get()).style("GOOGLE").formatJavadoc(true)

        formatAnnotations()

        licenseHeaderFile("copyright.txt") // contains '$YEAR' placeholder
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
}


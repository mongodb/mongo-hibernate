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
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("eclipse")
    id("idea")
    id("java-library")
    id("spotless-java-extension")
    alias(libs.plugins.errorprone)
    alias(libs.plugins.buildconfig)
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Integration Test

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestSourceSet: SourceSet = sourceSets["integrationTest"]

val integrationTestImplementation: Configuration by
    configurations.getting { extendsFrom(configurations.implementation.get()) }
val integrationTestRuntimeOnly: Configuration by
    configurations.getting { extendsFrom(configurations.runtimeOnly.get()) }

val integrationTestTask =
    tasks.register<Test>("integrationTest") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
    }

tasks.check { dependsOn(integrationTestTask) }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED) }
}

// https://youtrack.jetbrains.com/issue/IDEA-234382/Gradle-integration-tests-are-not-marked-as-test-sources-resources
idea {
    module {
        testSources.from(integrationTestSourceSet.allSource.srcDirs)
        testResources.from(integrationTestSourceSet.resources.srcDirs)
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Static Analysis

spotless {

    // java config is centralized in 'spotless-java-extension' gradle plugin

    kotlinGradle {
        ktfmt(libs.versions.plugin.ktfmt.get()).configure {
            it.setMaxWidth(120)
            it.setBlockIndent(4)
        }
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
}

tasks.check { dependsOn(tasks.spotlessApply) }

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    when (this) {
        tasks.compileJava.get() ->
            options.errorprone {
                disableWarningsInGeneratedCode = true
                option("NullAway:AnnotatedPackages", "com.mongodb.hibernate")
                error("NullAway")
            }
        else -> options.errorprone.isEnabled = false
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Build Config

buildConfig {
    useJavaOutput()
    packageName("com.mongodb.hibernate.internal")
    buildConfigField("NAME", provider { project.name })
    buildConfigField("VERSION", provider { "${project.version}" })
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Dependencies

dependencies {
    testImplementation(libs.bundles.test.common)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testCompileOnly(libs.checker.qual)

    integrationTestImplementation(libs.bundles.test.common)
    integrationTestImplementation(libs.hibernate.testing) {
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    integrationTestRuntimeOnly(libs.junit.platform.launcher)

    api(libs.jspecify)

    errorprone(libs.nullaway)
    errorprone(libs.google.errorprone.core)

    implementation(libs.hibernate.core)
    implementation(libs.mongo.java.driver.sync)
    implementation(libs.sl4j.api)
}

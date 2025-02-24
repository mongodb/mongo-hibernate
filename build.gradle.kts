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

import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import java.io.Serializable
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestLogEvent

version = "1.0.0-SNAPSHOT"

plugins {
    idea
    `java-library`
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.buildconfig)
}

repositories { mavenCentral() }

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

val integrationTestTask: Task =
    task<Test>("integrationTest") {
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
    java {
        importOrder()

        removeUnusedImports()

        palantirJavaFormat(libs.versions.palantir.get()).formatJavadoc(true)

        formatAnnotations()

        // need to add license header manually to package-info.java
        // due to the bug: https://github.com/diffplug/spotless/issues/532
        licenseHeaderFile("spotless.license.java") // contains '$YEAR' placeholder

        targetExclude("${layout.buildDirectory.get().asFile.name}/generated/**/*.java")

        val formatter = MultilineFormatter()
        addStep(
            FormatterStep.create(
                "multilineFormatter",
                object : Serializable {},
                { _: Any? -> FormatterFunc { input: String -> formatter.format(input) } }))
    }

    kotlinGradle {
        ktfmt(libs.versions.ktfmt.get()).configure {
            it.setMaxWidth(120)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }
    }
}

/** Format multiline strings to match the initial """ indentation level */
class MultilineFormatter : Serializable {
    fun format(content: String): String {
        val tripleQuote = "\"\"\""
        val lines = content.lines()
        val result = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.trimEnd().endsWith(tripleQuote)) {
                result.append(line)
                if (i + 1 < lines.size) result.append("\n")
                i++
                continue
            }
            val baseIndent = line.indexOf(tripleQuote)
            result.append(line).append("\n")
            i++
            val multilineStringLines = mutableListOf<String>()
            while (i < lines.size) {
                val multilineStringLine = lines[i++]
                multilineStringLines.add(multilineStringLine)
                if (multilineStringLine.contains(tripleQuote)) break
            }
            val minIndent =
                multilineStringLines
                    .filter { it.isNotBlank() }
                    .map { l -> l.indexOfFirst { ch -> !ch.isWhitespace() }.takeIf { it >= 0 } ?: line.length }
                    .minOrNull() ?: 0
            multilineStringLines.forEach { blockLine ->
                result.append(" ".repeat(baseIndent)).append(blockLine.drop(minIndent)).append("\n")
            }
        }
        return result.toString()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    if (name == "compileJava") {
        options.errorprone {
            disableWarningsInGeneratedCode.set(true)
            option("NullAway:AnnotatedPackages", "com.mongodb.hibernate")
            error("NullAway")
        }
    } else {
        options.errorprone.isEnabled.set(false)
    }
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
    testImplementation(libs.assertj)
    testImplementation(libs.logback.classic)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    integrationTestImplementation(libs.junit.jupiter)
    integrationTestImplementation(libs.assertj)
    integrationTestImplementation(libs.logback.classic)

    @Suppress("UnstableApiUsage")
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

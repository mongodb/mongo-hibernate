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

import java.time.Duration
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("eclipse")
    id("mongo-hibernate-java")
    id("mongo-hibernate-integration-test")
    id("mongo-hibernate-publish")
    alias(libs.plugins.errorprone)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.nexus.publish)
}

repositories { mavenCentral() }

tasks.withType<Javadoc> {
    val standardDocletOptions = options as StandardJavadocDocletOptions
    standardDocletOptions.apply {
        addBooleanOption("Werror", false)
        // TODO-HIBERNATE-129 addStringOption("-link-modularity-mismatch", "info")
        addBooleanOption("serialwarn", true)
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Xdoclint/package:-com.mongodb.hibernate.internal.*", true)
        addStringOption("-show-module-contents", "api")
        addStringOption("-show-packages", "exported")
        addStringOption("-show-types", "protected")
        author(true)
        version(true)
        encoding("UTF-8")
        charSet("UTF-8")
        docEncoding("UTF-8")
        addBooleanOption("html5", true)
        addBooleanOption("-allow-script-in-comments", true)
        links =
            listOf(
                "https://docs.oracle.com/en/java/javase/17/docs/api/",
                "https://jakarta.ee/specifications/persistence/3.1/apidocs/",
                "https://docs.hibernate.org/orm/7.4/javadocs/",
                "https://mongodb.github.io/mongo-java-driver/5.6/apidocs/bson/",
                "https://mongodb.github.io/mongo-java-driver/5.6/apidocs/driver-core/",
                "https://mongodb.github.io/mongo-java-driver/5.6/apidocs/driver-sync/",
                "https://jspecify.dev/docs/api",
            )
        // specify the custom `@mongoCme` `javadoc` block tag
        tags("mongoCme:TM:Concurrency, Mutability, Execution\\:")
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Static Analysis

spotless {
    java {
        importOrder()

        removeUnusedImports()

        palantirJavaFormat(libs.versions.plugin.palantir.get()).formatJavadoc(true)

        formatAnnotations()

        // need to add license header manually to package-info.java and module-info.java
        // due to the bug: https://github.com/diffplug/spotless/issues/532
        licenseHeaderFile(file("spotless.license.java")) // contains '$YEAR' placeholder

        targetExclude("build/generated/sources/buildConfig/**/*.java")
    }

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

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf("-Xlint:all", "-Xlint:-requires-automatic", "-Xlint:-requires-transitive-automatic", "-Werror")
    )
    when (this) {
        tasks.compileJava.get() ->
            options.errorprone {
                disableWarningsInGeneratedCode = true
                // Error Prone does not understand the `@hidden` standard tag.
                // It also complains about the `javadoc` tags registered via the `-tag`/`-taglet` options
                disable("InvalidBlockTag")
                disable("AssignmentExpression")
                option("NullAway:AnnotatedPackages", "com.mongodb.hibernate")
                error("NullAway")
            }
        else -> options.errorprone.isEnabled = false
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Integration test parallelism
//
// This module's integration tests are isolated per fork (each fork gets its own database; see MongoExtension) and so
// run in parallel via `integrationTest`. Tests tagged "serial" manipulate mongod-global state (the `failCommand` fail
// point, a single shared server-side switch) and cannot tolerate concurrent forks, so they run in a separate
// single-fork `integrationTestSerial` task. That task finalizes `integrationTest`, so `./gradlew integrationTest` (and
// anything that depends on it, such as `check`) runs the whole integration suite — the two-task split is otherwise an
// invisible implementation detail. This configuration is deliberately not in the shared
// `mongo-hibernate-integration-test` convention plugin: the Spring Boot modules reuse that plugin but their tests are
// not fork-isolated.

val integrationTestSerial =
    tasks.register<Test>("integrationTestSerial") {
        val integrationTest = tasks.named<Test>("integrationTest").get()
        group = integrationTest.group
        testClassesDirs = integrationTest.testClassesDirs
        classpath = integrationTest.classpath
        maxParallelForks = 1
        useJUnitPlatform { includeTags("serial") }
    }

tasks.named<Test>("integrationTest") {
    // Fork count defaults to half the logical CPUs (portable across machines and CI). The fastest value is
    // machine-specific and does not track core count in any simple way (empirically it can peak well below the
    // CPU count and regress above it), so it is overridable per machine via `-PitForks=<n>` or an `itForks=<n>`
    // line in `~/.gradle/gradle.properties`.
    maxParallelForks =
        (findProperty("itForks") as String?)?.toInt()
            ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    useJUnitPlatform { excludeTags("serial") }
    // Finalizer, so running `integrationTest` alone still runs the serial tests (strictly after this task, never
    // overlapping — both share the one mongod, and the serial tests toggle a global fail point).
    finalizedBy(integrationTestSerial)
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Build Config

buildConfig {
    useJavaOutput()
    packageName("com.mongodb.hibernate.internal")
    documentation.set(
        "Generated by the <a href=\"https://github.com/gmazzo/gradle-buildconfig-plugin\">BuildConfig</a> plugin.\n\n@hidden"
    )
    buildConfigField("NAME", provider { project.name })
    buildConfigField("VERSION", provider { "${project.version}" })
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Dependencies

dependencies {
    testImplementation(libs.bundles.test.common)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly(libs.checker.qual)

    integrationTestImplementation(libs.bundles.test.common)
    integrationTestImplementation(libs.hibernate.testing) {
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher")

    api(libs.jspecify)

    errorprone(libs.nullaway)
    errorprone(libs.google.errorprone.core)

    implementation(platform(libs.hibernate.platform))
    api(libs.hibernate.core)
    implementation(libs.hibernate.models)
    api(libs.mongo.java.driver.sync)
    // We need the `libs.findbugs.jsr` dependency to stop `javadoc` from emitting
    // `warning: unknown enum constant When.MAYBE`
    //   `reason: class file for javax.annotation.meta.When not found`.
    compileOnly(libs.findbugs.jsr)
    implementation(libs.sl4j.api)
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Publishing

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "org.mongodb"
            artifactId = "mongodb-hibernate"
            from(components["java"])
            pom {
                name = "MongoDB Extension for Hibernate ORM"
                description = "An extension providing MongoDB support to Hibernate ORM"
                // url, licenses, developers, scm injected by mongo-hibernate-publish plugin
            }
        }
    }
}

nexusPublishing {
    packageGroup.set("org.mongodb")
    repositories {
        sonatype {
            username.set(providers.gradleProperty("nexusUsername"))
            password.set(providers.gradleProperty("nexusPassword"))

            // central portal URLs
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }

    connectTimeout.set(Duration.ofMinutes(5))
    clientTimeout.set(Duration.ofMinutes(30))

    transitionCheckOptions {
        // We have many artifacts and Maven Central can take a long time on its compliance checks.
        // Set the timeout for waiting for the repository to close to a comfortable 50 minutes.
        maxRetries.set(300)
        delayBetween.set(Duration.ofSeconds(10))
    }
}

// Gets the git version
val gitVersion: String by lazy {
    providers
        .exec {
            isIgnoreExitValue = true
            commandLine("git", "describe", "--tags", "--always", "--dirty")
        }
        .standardOutput
        .asText
        .map { it.trim().removePrefix("r") }
        .getOrElse("UNKNOWN")
}

// Publish snapshots
tasks.register("publishSnapshots") {
    group = "publishing"
    description = "Publishes snapshots to Sonatype"

    if (version.toString().endsWith("-SNAPSHOT")) {
        dependsOn(tasks.named("publishAllPublicationsToLocalBuildRepository"))
        dependsOn(tasks.named("publishToSonatype"))
    }
}

// Publish the release
tasks.register("publishArchives") {
    group = "publishing"
    description = "Publishes a release and uploads to Sonatype / Maven Central"

    val currentGitVersion = gitVersion
    val gitVersionMatch = currentGitVersion == version
    doFirst {
        if (!gitVersionMatch) {
            val cause =
                """
                Version mismatch:
                =================

                 $version != $currentGitVersion

                 The project version does not match the git tag.
                """
                    .trimMargin()
            throw GradleException(cause)
        } else {
            println("Publishing: ${project.name} : $currentGitVersion")
        }
    }
    if (gitVersionMatch) {
        dependsOn(tasks.named("publishAllPublicationsToLocalBuildRepository"))
        dependsOn(tasks.named("publishToSonatype"))
    }
}

// `./gradlew -q printProjectVersion`
tasks.register("printProjectVersion") { doLast { logger.quiet(project.version.toString()) } }

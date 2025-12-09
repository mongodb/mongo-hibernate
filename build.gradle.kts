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
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("eclipse")
    id("idea")
    id("java-library")
    id("spotless-java-extension")
    id("maven-publish")
    id("signing")
    alias(libs.plugins.errorprone)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.nexus.publish)
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) } // Remember to update javadoc links
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Javadoc> {
    val standardDocletOptions = options as StandardJavadocDocletOptions
    standardDocletOptions.apply {
        addBooleanOption("Werror", false)
        // TODO-HIBERNATE-129 addStringOption("-link-modularity-mismatch", "info")
        addBooleanOption("serialwarn", true)
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption(
            "Xdoclint/package:-" +
                "com.mongodb.hibernate.internal.*" +
                ",com.mongodb.hibernate.dialect.*" +
                ",com.mongodb.hibernate.jdbc.*",
            true)
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
                "https://docs.hibernate.org/orm/6.6/javadocs/",
                "https://mongodb.github.io/mongo-java-driver/5.6/apidocs/bson/",
                "https://mongodb.github.io/mongo-java-driver/5.6/apidocs/driver-core/",
                "https://mongodb.github.io/mongo-java-driver/5.6/apidocs/driver-sync/",
                "https://javadoc.io/doc/org.jspecify/jspecify/1.0.0/")
        // specify the custom `@mongoCme` `javadoc` block tag
        tags("mongoCme:TM:Concurrency, Mutability, Execution\\:")
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Integration Test

// Added `Action` explicitly due to an intellij 2025.2 false positive: https://youtrack.jetbrains.com/issue/KTIJ-34210
sourceSets {
    create(
        "integrationTest",
        Action {
            compileClasspath += sourceSets.main.get().output
            runtimeClasspath += sourceSets.main.get().output
        })
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

tasks.check { dependsOn(tasks.spotlessApply) }

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf("-Xlint:all", "-Xlint:-requires-automatic", "-Xlint:-requires-transitive-automatic", "-Werror"))
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
// Build Config

buildConfig {
    useJavaOutput()
    packageName("com.mongodb.hibernate.internal")
    documentation.set(
        "Generated by the <a href=\"https://github.com/gmazzo/gradle-buildconfig-plugin\">BuildConfig</a> plugin.\n\n@hidden")
    buildConfigField("NAME", provider { project.name })
    buildConfigField("VERSION", provider { "${project.version}" })
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Dependencies

dependencies {
    testImplementation(libs.bundles.test.common)
    testImplementation(libs.mockito.junit.jupiter)
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

    api(libs.hibernate.core)
    api(libs.mongo.java.driver.sync)
    // We need the `libs.findbugs.jsr` dependency to stop `javadoc` from emitting
    // `warning: unknown enum constant When.MAYBE`
    //   `reason: class file for javax.annotation.meta.When not found`.
    compileOnly(libs.findbugs.jsr)
    implementation(libs.sl4j.api)
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Publishing

val localBuildRepo: Provider<Directory> = project.layout.buildDirectory.dir("repo")

tasks.named<Delete>("clean") { delete.add(localBuildRepo) }

tasks.withType<GenerateModuleMetadata> { enabled = false }

publishing {
    repositories {
        // publish to local build dir for testing
        // `./gradlew publishMavenPublicationToLocalBuildRepository`
        //
        // publish to the local Maven cache
        // `./gradlew publishToMavenLocal`
        maven {
            url = uri(localBuildRepo.get())
            name = "LocalBuild"
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "org.mongodb"
            artifactId = "mongodb-hibernate"
            from(components["java"])
            pom {
                name = "MongoDB Extension for Hibernate ORM"
                description = "An extension providing MongoDB support to Hibernate ORM"
                url = "https://www.mongodb.com/"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        name.set("Various")
                        organization.set("MongoDB")
                    }
                }
                scm {
                    url.set("https://github.com/mongodb/mongo-hibernate")
                    connection.set("scm:git:https://github.com/mongodb/mongo-hibernate.git")
                    developerConnection.set("scm:git:https://github.com/mongodb/mongo-hibernate.git")
                }
            }
        }
    }
}

// Artifact signing
signing {
    val signingKey: String? = providers.gradleProperty("signingKey").getOrNull()
    val signingPassword: String? = providers.gradleProperty("signingPassword").getOrNull()
    if (signingKey != null && signingPassword != null) {
        logger.info("[${project.displayName}] Signing is enabled")
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    } else {
        logger.info("[${project.displayName}] No Signing keys found, skipping signing configuration")
    }
}

// Publishing to the central sonatype portal currently requires the gradle nexus publishing plugin
// Adds a `publishToSonatype` task
val nexusUsername: Provider<String> = providers.gradleProperty("nexusUsername")
val nexusPassword: Provider<String> = providers.gradleProperty("nexusPassword")

nexusPublishing {
    packageGroup.set("org.mongodb")
    repositories {
        sonatype {
            username.set(nexusUsername)
            password.set(nexusPassword)

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

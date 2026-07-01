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
    id("idea")
}

// Added `Action` explicitly due to an intellij 2025.2 false positive: https://youtrack.jetbrains.com/issue/KTIJ-34210
sourceSets {
    create(
        "integrationTest",
        Action {
            compileClasspath += sourceSets.main.get().output
            runtimeClasspath += sourceSets.main.get().output
        },
    )
}

val integrationTestSourceSet: SourceSet = sourceSets["integrationTest"]

val integrationTestImplementation: Configuration by
    configurations.getting { extendsFrom(configurations.implementation.get()) }
val integrationTestRuntimeOnly: Configuration by
    configurations.getting { extendsFrom(configurations.runtimeOnly.get()) }

tasks.register<Test>("integrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
}

tasks.check { dependsOn(tasks.named("integrationTest")) }

// https://youtrack.jetbrains.com/issue/IDEA-234382/Gradle-integration-tests-are-not-marked-as-test-sources-resources
idea {
    module {
        testSources.from(integrationTestSourceSet.allSource.srcDirs)
        testResources.from(integrationTestSourceSet.resources.srcDirs)
    }
}

/*
 * Copyright 2008-present MongoDB, Inc.
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

package project

plugins {
    `java-library`
}

logger.info("Compiling ${project.name} using JDK${RELEASE_JAVA_VERSION}")

java {
    toolchain { languageVersion = JavaLanguageVersion.of(RELEASE_JAVA_VERSION) }
}

tasks.withType<JavaCompile> {
    options.release.set(RELEASE_JAVA_VERSION)
}

tasks.withType<Test>().configureEach {
    val testJavaVersion: Int = (findProperty("javaVersion") as String?)?.toInt() ?: RELEASE_JAVA_VERSION
    logger.info("Running tests using using JDK${testJavaVersion}")
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
        }
    )
}
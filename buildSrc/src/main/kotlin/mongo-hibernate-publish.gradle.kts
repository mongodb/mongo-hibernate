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
    id("maven-publish")
    id("signing")
}

val localBuildRepo: Provider<Directory> = project.layout.buildDirectory.dir("repo")

tasks.named<Delete>("clean") { delete.add(localBuildRepo) }

tasks.withType<GenerateModuleMetadata> { enabled = false }

publishing {
    repositories {
        // publish to local build dir for testing:
        //   ./gradlew publishMavenPublicationToLocalBuildRepository
        // publish to the local Maven cache:
        //   ./gradlew publishToMavenLocal
        maven {
            url = uri(localBuildRepo.get())
            name = "LocalBuild"
        }
    }
}

// Inject the common POM fields into every MavenPublication defined in the consuming project.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
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

signing {
    val signingKey: String? = providers.gradleProperty("signingKey").getOrNull()
    val signingPassword: String? = providers.gradleProperty("signingPassword").getOrNull()
    if (signingKey != null && signingPassword != null) {
        logger.info("[${project.displayName}] Signing is enabled")
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications) // signs all publications in the project, not just a named one
    } else {
        logger.info("[${project.displayName}] No Signing keys found, skipping signing configuration")
    }
}

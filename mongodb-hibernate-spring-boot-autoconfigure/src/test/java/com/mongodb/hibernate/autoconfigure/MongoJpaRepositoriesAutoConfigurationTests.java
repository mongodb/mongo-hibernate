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

package com.mongodb.hibernate.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;

class MongoJpaRepositoriesAutoConfigurationTests {

    // Register as an auto-configuration (not withUserConfiguration) so @ConditionalOnMissingBean
    // orders after user-supplied beans — same requirement as MongoHibernateAutoConfigurationTests.
    // Each test observes whether the auto-configuration fired by the presence of its own
    // @Configuration bean, which works without any repository interfaces or an EntityManagerFactory
    // (the positive path — repositories actually created — needs MongoDB and lives in the
    // integration test).
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MongoJpaRepositoriesAutoConfiguration.class));

    @Test
    void backsOffIfRepositoryConfigurationAlreadyDefined() {
        // A user who declares their own @EnableJpaRepositories yields a JpaRepositoryConfigExtension
        // (and JpaRepositoryFactoryBean); our auto-configuration must defer to them. The MongoDB platform
        // makes OnMongoDatabasePlatformCondition match, so this isolates the @ConditionalOnMissingBean
        // back-off.
        contextRunner
                .withPropertyValues("spring.jpa.database-platform=MongoDB")
                .withBean(JpaRepositoryConfigExtension.class, () -> mock(JpaRepositoryConfigExtension.class))
                .run(context -> assertThat(context).doesNotHaveBean(MongoJpaRepositoriesAutoConfiguration.class));
    }

    @Test
    void doesNotActivateWhenSpringDataJpaAbsentFromClasspath() {
        // FilteredClassLoader simulates Spring Data JPA being absent without changing the build.
        contextRunner
                .withPropertyValues("spring.jpa.database-platform=MongoDB")
                .withClassLoader(new FilteredClassLoader(JpaRepository.class))
                .run(context -> assertThat(context).doesNotHaveBean(MongoJpaRepositoriesAutoConfiguration.class));
    }

    @Test
    void doesNotActivateForNonMongoPlatform() {
        // A SQL application with this module on its classpath must be left alone.
        contextRunner
                .withPropertyValues("spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
                .run(context -> assertThat(context).doesNotHaveBean(MongoJpaRepositoriesAutoConfiguration.class));
    }
}

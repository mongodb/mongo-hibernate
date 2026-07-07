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

import java.lang.annotation.Annotation;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.AbstractRepositoryConfigurationSourceSupport;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;

/**
 * Spring Boot {@link AutoConfiguration auto-configuration} that enables Spring Data JPA repository scanning for
 * MongoDB-backed persistence.
 *
 * <p>Spring Boot's own {@code DataJpaRepositoriesAutoConfiguration} only activates when a {@code DataSource} bean is
 * present. This module provides no {@code DataSource}, so that auto-configuration never fires; this class is an
 * equivalent that drops the {@code DataSource} requirement. It activates when {@link JpaRepository} is on the
 * classpath, {@code spring.jpa.database-platform} is {@code MongoDB}, and no {@link JpaRepositoryFactoryBean} has already
 * been defined, so a user who declares {@code @EnableJpaRepositories} themselves takes precedence.
 */
@AutoConfiguration(
        after = MongoHibernateAutoConfiguration.class,
        beforeName = "org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration")
@ConditionalOnClass(JpaRepository.class)
// Same gate as MongoHibernateAutoConfiguration: activate only when spring.jpa.database-platform=MongoDB,
// so a SQL application with this module on its classpath is unaffected.
@Conditional(OnMongoDatabasePlatformCondition.class)
@ConditionalOnMissingBean({JpaRepositoryFactoryBean.class, JpaRepositoryConfigExtension.class})
@ConditionalOnBooleanProperty(name = "spring.data.jpa.repositories.enabled", matchIfMissing = true)
@Import(MongoJpaRepositoriesAutoConfiguration.RepositoriesRegistrar.class)
public class MongoJpaRepositoriesAutoConfiguration {

    /** Creates a new {@code MongoJpaRepositoriesAutoConfiguration}. */
    public MongoJpaRepositoriesAutoConfiguration() {}

    /**
     * Registers the Spring Data JPA repository infrastructure. {@link AbstractRepositoryConfigurationSourceSupport} is
     * the Spring Boot base class that wires {@code @EnableJpaRepositories} using {@code AutoConfigurationPackages} (the
     * main application package) for the scan base, rather than the package of the annotated class.
     */
    static final class RepositoriesRegistrar extends AbstractRepositoryConfigurationSourceSupport {

        RepositoriesRegistrar() {}

        @Override
        protected Class<? extends Annotation> getAnnotation() {
            return EnableJpaRepositories.class;
        }

        @Override
        protected Class<?> getConfiguration() {
            return EnableJpaRepositoriesConfiguration.class;
        }

        @Override
        protected RepositoryConfigurationExtension getRepositoryConfigurationExtension() {
            return new JpaRepositoryConfigExtension();
        }
    }

    @EnableJpaRepositories
    private static class EnableJpaRepositoriesConfiguration {}
}

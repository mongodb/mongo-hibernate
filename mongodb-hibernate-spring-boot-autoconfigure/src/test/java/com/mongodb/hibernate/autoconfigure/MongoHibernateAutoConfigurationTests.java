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

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

class MongoHibernateAutoConfigurationTests {

    private static final String PHYSICAL_NAMING_STRATEGY = "hibernate.physical_naming_strategy";
    private static final String IMPLICIT_NAMING_STRATEGY = "hibernate.implicit_naming_strategy";

    private static final String ACTIVATE = "spring.jpa.database-platform=MongoDB";

    // Register as an auto-configuration (not withUserConfiguration) so Spring processes it AFTER
    // user-supplied beans. @ConditionalOnMissingBean is only ordering-reliable for auto-configs.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MongoHibernateAutoConfiguration.class));

    // Registers a stub MongoConnectionDetails (returning a connection string with a database), a mock
    // MongoClient, and a MongoProperties bean, and sets the activation property — so the bridge resolves and
    // the no-client guard backs off. Generic over the runner type so both the plain and web runners qualify.
    private static <SELF extends org.springframework.boot.test.context.runner.AbstractApplicationContextRunner<
                            SELF, C, A>,
                    C extends org.springframework.context.ConfigurableApplicationContext,
                    A extends org.springframework.boot.test.context.assertj.ApplicationContextAssertProvider<C>>
            SELF withMongoClient(SELF runner) {
        return runner.withPropertyValues(ACTIVATE)
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoProperties.class, MongoProperties::new)
                .withBean(MongoConnectionDetails.class, () -> (MongoConnectionDetails)
                        () -> new ConnectionString("mongodb://localhost/test"));
    }

    @Test
    void backsOffIfEntityManagerFactoryAlreadyDefined() {
        // With the integration active and a client to borrow, this isolates the @ConditionalOnMissingBean
        // back-off rather than the activation gate. A user-supplied EntityManagerFactory must suppress ours.
        withMongoClient(contextRunner)
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LocalContainerEntityManagerFactoryBean.class);
                    assertThat(context).hasSingleBean(EntityManagerFactory.class);
                });
    }

    @Test
    void doesNotActivateForNonMongoPlatform() {
        // A SQL application that happens to have this module on its classpath must be left alone:
        // OnMongoDatabasePlatformCondition does not match a non-MongoDB platform, so nothing is contributed.
        contextRunner
                .withPropertyValues("spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LocalContainerEntityManagerFactoryBean.class);
                    assertThat(context).doesNotHaveBean(EntityManagerFactoryBuilder.class);
                    assertThat(context).doesNotHaveBean(JpaTransactionManager.class);
                });
    }

    @Test
    void doesNotActivateWithoutDatabasePlatform() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(LocalContainerEntityManagerFactoryBean.class);
            assertThat(context).doesNotHaveBean(JpaTransactionManager.class);
        });
    }

    @Test
    void staysInertWhenMongoClientPresentButPlatformUnset() {
        // The no-hijack case: a Spring Data MongoDB app has a MongoClient but has not opted into Mongo JPA.
        contextRunner
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .run(context -> assertThat(context).doesNotHaveBean(LocalContainerEntityManagerFactoryBean.class));
    }

    @Test
    void failsFastWhenActivatedWithoutMongoClient() {
        contextRunner
                .withPropertyValues(ACTIVATE)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("no MongoClient bean is available"));
    }

    @Test
    void failsFastWhenNoDatabaseResolvedFromAnySource() {
        // MongoClient present (guard passes), but neither spring.mongodb.database nor the connection string
        // carries a database, so the bridge cannot resolve one.
        contextRunner
                .withPropertyValues(ACTIVATE)
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoProperties.class, MongoProperties::new) // getDatabase() == null
                .withBean(MongoConnectionDetails.class, () -> (MongoConnectionDetails)
                        () -> new ConnectionString("mongodb://localhost")) // no database in the connection string
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("database name is required"));
    }

    @Test
    void userContributorTakesOverAndBridgeAndGuardBackOff() {
        // A user MongoConfigurationContributor signals "I supply the connection myself": the bridge and the
        // no-client guard both defer to it.
        MongoConfigurationContributor userContributor = configurator -> {};
        contextRunner
                .withPropertyValues(ACTIVATE)
                .withBean(MongoConfigurationContributor.class, () -> userContributor)
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // No MongoClient to borrow — this is exactly the setup failsFastWhenActivatedWithoutMongoClient
                    // proves would throw; the user contributor is what suppresses the guard.
                    assertThat(context).doesNotHaveBean(MongoClient.class);
                    // The user's contributor took over: it is the only MongoConfigurationContributor in play, so the
                    // bridge (itself a MongoConfigurationContributor, named mongoClientConfigurationContributor)
                    // registered no competing bean.
                    assertThat(context).hasSingleBean(MongoConfigurationContributor.class);
                    assertThat(context).getBean(MongoConfigurationContributor.class).isSameAs(userContributor);
                    assertThat(context).doesNotHaveBean("mongoClientConfigurationContributor");
                });
    }

    @Test
    void bridgeContributorIsRegisteredWhenClientPresent() {
        withMongoClient(contextRunner)
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(context).hasBean("mongoClientConfigurationContributor"));
    }

    @Test
    void doesNotRegisterBridgeWhenSpringBootMongodbAbsent() {
        // When MongoConnectionDetails is filtered out, the no-client guard still sees the MongoClient bean from
        // withMongoClient, so the context does not fail; the bridge simply is not registered.
        withMongoClient(contextRunner)
                .withClassLoader(new FilteredClassLoader(MongoConnectionDetails.class))
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(context).doesNotHaveBean("mongoClientConfigurationContributor"));
    }

    @Test
    void mongoEntityManagerFactoryWinsOverSqlWhenDataSourcePresent() {
        // Verifies the dropped EnvironmentPostProcessor exclusion is correctly replaced by before-ordering:
        // with a stray DataSource present, only the @Primary Mongo EntityManagerFactory exists and Spring's
        // SQL JPA backs off.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                        org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
                        MongoHibernateAutoConfiguration.class))
                .withPropertyValues(ACTIVATE)
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoProperties.class, MongoProperties::new)
                .withBean(MongoConnectionDetails.class, () -> (MongoConnectionDetails)
                        () -> new ConnectionString("mongodb://localhost/test"))
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(context).hasSingleBean(EntityManagerFactory.class));
    }

    @Test
    void honorsDdlAutoAndDefaultsToHibernateCamelCaseNaming() {
        withMongoClient(contextRunner)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=update")
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> {
                    var properties = assembledJpaProperties(context);
                    assertThat(properties).containsEntry("hibernate.hbm2ddl.auto", "update");
                    // The default physical naming strategy is Hibernate's identity mapping (camelCase),
                    // NOT Spring Boot's snake_case default.
                    assertThat(properties)
                            .containsEntry(
                                    PHYSICAL_NAMING_STRATEGY, PhysicalNamingStrategyStandardImpl.class.getName());
                    // The default implicit naming strategy is Hibernate's JPA-compliant one (matching the
                    // standalone extension), NOT Spring Boot's SpringImplicitNamingStrategy.
                    assertThat(properties)
                            .containsEntry(
                                    IMPLICIT_NAMING_STRATEGY, ImplicitNamingStrategyJpaCompliantImpl.class.getName());
                });
    }

    @Test
    void honorsExplicitImplicitNamingStrategyOverride() {
        // As with the physical strategy, an explicit spring.jpa.hibernate.naming.implicit-strategy must win
        // over the seeded default (putIfAbsent does not clobber it).
        withMongoClient(contextRunner)
                .withPropertyValues("spring.jpa.hibernate.naming.implicit-strategy="
                        + ImplicitNamingStrategyComponentPathImpl.class.getName())
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(assembledJpaProperties(context))
                        .containsEntry(
                                IMPLICIT_NAMING_STRATEGY, ImplicitNamingStrategyComponentPathImpl.class.getName()));
    }

    @Test
    void honorsExplicitPhysicalNamingStrategyOverride() {
        // A user who explicitly asks for snake_case gets it — the default is changed by *presence* of
        // the property, never by comparing its value, so even requesting Spring Boot's own default works.
        withMongoClient(contextRunner)
                .withPropertyValues("spring.jpa.hibernate.naming.physical-strategy="
                        + PhysicalNamingStrategySnakeCaseImpl.class.getName())
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(assembledJpaProperties(context))
                        .containsEntry(PHYSICAL_NAMING_STRATEGY, PhysicalNamingStrategySnakeCaseImpl.class.getName()));
    }

    @Test
    void appliesHibernatePropertiesCustomizerBeans() {
        withMongoClient(contextRunner)
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(
                        HibernatePropertiesCustomizer.class,
                        () -> (HibernatePropertiesCustomizer) properties -> properties.put("custom.key", "custom.value"))
                .run(context -> assertThat(assembledJpaProperties(context))
                        .containsEntry("custom.key", "custom.value"));
    }

    @Test
    void registersOpenEntityManagerInViewInterceptorByDefaultInServletWebApp() {
        withMongoClient(webContextRunner())
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(context).hasSingleBean(OpenEntityManagerInViewInterceptor.class));
    }

    @Test
    void omitsOpenEntityManagerInViewInterceptorWhenOpenInViewDisabled() {
        withMongoClient(webContextRunner())
                .withPropertyValues("spring.jpa.open-in-view=false")
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(context).doesNotHaveBean(OpenEntityManagerInViewInterceptor.class));
    }

    private static WebApplicationContextRunner webContextRunner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoHibernateAutoConfiguration.class));
    }

    // Drives property assembly without bootstrapping Hibernate: a user EntityManagerFactory makes our
    // entityManagerFactory bean back off (so nothing connects), but the EntityManagerFactoryBuilder bean
    // is still created. EntityManagerFactoryBuilder.build() only *configures* the factory bean — it does
    // not call afterPropertiesSet() — so getJpaPropertyMap() exposes the assembled properties with no
    // database. The builder is the public seam; no production visibility is widened for the test.
    @SuppressWarnings("NullAway") // MongoDB has no DataSource; null is the correct argument (see production code).
    private static Map<String, Object> assembledJpaProperties(AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean(LocalContainerEntityManagerFactoryBean.class);
        var builder = context.getBean(EntityManagerFactoryBuilder.class);
        return builder.dataSource(null)
                .packages("com.mongodb.hibernate.autoconfigure")
                .build()
                .getJpaPropertyMap();
    }
}

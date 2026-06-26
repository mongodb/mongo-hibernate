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

import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.cfg.MappingSettings;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryBuilderCustomizer;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot {@link AutoConfiguration auto-configuration} for MongoDB-backed JPA via the MongoDB Extension for
 * Hibernate ORM.
 *
 * <p>Activated when {@link MongoConfigurationContributor} is on the classpath and {@code spring.jpa.database-platform}
 * is {@code MongoDB}. It reuses Spring Boot's own JPA property machinery ({@link JpaProperties},
 * {@link HibernateProperties}, {@link EntityManagerFactoryBuilder}) so the full {@code spring.jpa.*} surface is honored
 * - {@code ddl-auto}, {@code show-sql}, naming strategies, {@link HibernatePropertiesCustomizer} and
 * {@link EntityManagerFactoryBuilderCustomizer} beans - rather than only the raw {@code spring.jpa.properties.*}
 * passthrough. Spring Boot's {@code HibernateJpaAutoConfiguration} cannot be reused directly because it requires a
 * {@code DataSource}; this module has none (Hibernate connects to MongoDB via {@code jakarta.persistence.jdbc.url}).
 *
 * <p>Unlike Spring Boot, the default physical naming strategy is left as Hibernate's own identity mapping (the Java
 * property name) rather than {@code snake_case}: MongoDB is schemaless, so a divergent naming default would silently
 * fork field names across documents instead of erroring as a relational schema would. An explicit
 * {@code spring.jpa.hibernate.naming.*} is still honored.
 *
 * <p>Any {@link MongoConfigurationContributor} beans are aggregated into a composite contributor (applied in
 * {@link org.springframework.core.annotation.Order @Order} order, last wins) and forwarded to Hibernate through the JPA
 * properties map.
 */
// afterName (string) for MongoAutoConfiguration because spring-boot-mongodb is optional and may be absent at
// runtime; ordering after it ensures the MongoClient bean is registered before this configuration's bean
// conditions are evaluated. before uses the HibernateJpaAutoConfiguration class directly because
// spring-boot-hibernate is a non-optional implementation dependency; this lets the @Primary Mongo
// EntityManagerFactory win and Spring's SQL JPA back off when a stray DataSource is present.
@AutoConfiguration(
        afterName = "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
        before = org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(MongoConfigurationContributor.class)
// Activate only for MongoDB-backed applications: OnMongoDatabasePlatformCondition matches when
// spring.jpa.database-platform is MongoDB, so this module stays inert in a SQL application even when it is on
// the classpath.
@Conditional(OnMongoDatabasePlatformCondition.class)
@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class})
@ImportRuntimeHints(MongoHibernateAutoConfiguration.MongoHibernateRuntimeHints.class)
public class MongoHibernateAutoConfiguration {

    // Same string as MongoConstants.MONGO_CONFIGURATION_CONTRIBUTOR_KEY in the core module.
    // Duplicated here because MongoConstants is in an unexported internal package.
    private static final String CONFIGURATION_CONTRIBUTOR_KEY =
            "com.mongodb.hibernate.configurationContributor";

    /** Creates a new {@code MongoHibernateAutoConfiguration}. */
    public MongoHibernateAutoConfiguration() {}

    /**
     * Fails fast with an actionable message when the integration is active but there is no {@link MongoClient} bean to
     * borrow (for example {@code spring-boot-mongodb} is absent and the application defines no {@code MongoClient}).
     * Without this, bootstrap fails later in Hibernate with a cryptic "jakarta.persistence.jdbc.url is required" error.
     *
     * <p>The guard is registered as an eagerly-instantiated {@link BeanFactoryPostProcessor} so it runs before any
     * regular singleton (in particular the {@code entityManagerFactory}), making the actionable message the surfaced
     * failure rather than a downstream symptom.
     */
    @Bean
    @ConditionalOnMissingBean({MongoClient.class, MongoConfigurationContributor.class})
    static BeanFactoryPostProcessor mongoClientRequired() {
        throw new IllegalStateException(
                "spring.jpa.database-platform=MongoDB is set but no MongoClient bean is available to borrow. "
                        + "Add the spring-boot-mongodb dependency and configure spring.mongodb.*, define a "
                        + "MongoClient @Bean, or configure JPA manually instead of using this auto-configuration.");
    }

    /**
     * Creates the JPA vendor adapter, applying the {@code spring.jpa.*} settings Spring Boot's
     * {@code JpaBaseConfiguration} applies to its own adapter.
     *
     * @param jpaProperties the bound {@code spring.jpa.*} properties
     * @return the configured vendor adapter
     */
    @Bean
    @ConditionalOnMissingBean
    public JpaVendorAdapter jpaVendorAdapter(JpaProperties jpaProperties) {
        var adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(jpaProperties.isShowSql());
        if (jpaProperties.getDatabase() != null) {
            adapter.setDatabase(jpaProperties.getDatabase());
        }
        if (jpaProperties.getDatabasePlatform() != null) {
            adapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
        }
        adapter.setGenerateDdl(jpaProperties.isGenerateDdl());
        return adapter;
    }

    /**
     * Creates the {@link EntityManagerFactoryBuilder}, wired with a JPA-properties factory that ignores its
     * {@code DataSource} argument (MongoDB has none). {@link EntityManagerFactoryBuilderCustomizer} beans are applied in
     * {@code @Order} order.
     *
     * @param jpaVendorAdapter the vendor adapter
     * @param jpaProperties the bound {@code spring.jpa.*} properties
     * @param hibernateProperties the bound {@code spring.jpa.hibernate.*} properties
     * @param contributors the {@link MongoConfigurationContributor} beans to aggregate
     * @param hibernateCustomizers the {@link HibernatePropertiesCustomizer} beans to apply
     * @param builderCustomizers the {@link EntityManagerFactoryBuilderCustomizer} beans to apply
     * @return the configured builder
     */
    @Bean
    @ConditionalOnMissingBean
    public EntityManagerFactoryBuilder mongoEntityManagerFactoryBuilder(
            JpaVendorAdapter jpaVendorAdapter,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties,
            ObjectProvider<MongoConfigurationContributor> contributors,
            ObjectProvider<HibernatePropertiesCustomizer> hibernateCustomizers,
            ObjectProvider<EntityManagerFactoryBuilderCustomizer> builderCustomizers) {
        var builder = new EntityManagerFactoryBuilder(
                jpaVendorAdapter,
                dataSource -> buildJpaProperties(jpaProperties, hibernateProperties, contributors, hibernateCustomizers),
                null);
        builderCustomizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    private Map<String, Object> buildJpaProperties(
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties,
            ObjectProvider<MongoConfigurationContributor> contributors,
            ObjectProvider<HibernatePropertiesCustomizer> hibernateCustomizers) {
        // Seed Hibernate's own default naming strategies so we do NOT inherit Spring Boot's snake_case
        // default. putIfAbsent means an explicit spring.jpa.hibernate.naming.* (which Spring Boot applies
        // with put) or a raw spring.jpa.properties.hibernate.*_naming_strategy still wins; only the unset
        // case falls back to Hibernate's identity (camelCase) mapping. A schemaless store must not
        // silently reshape field names.
        var base = new HashMap<>(jpaProperties.getProperties());
        base.putIfAbsent(
                MappingSettings.IMPLICIT_NAMING_STRATEGY, ImplicitNamingStrategyJpaCompliantImpl.class.getName());
        base.putIfAbsent(MappingSettings.PHYSICAL_NAMING_STRATEGY, PhysicalNamingStrategyStandardImpl.class.getName());

        var settings = new HibernateSettings()
                // MongoDB has no embedded-database notion, so Spring Boot's DataSource-dependent
                // HibernateDefaultDdlAutoProvider is not used; default ddl-auto to "none".
                .ddlAuto(() -> "none")
                .hibernatePropertiesCustomizers(
                        hibernateCustomizers.orderedStream().toList());

        var properties = new HashMap<>(hibernateProperties.determineHibernateProperties(base, settings));

        var contributorList = contributors.orderedStream().toList();
        if (!contributorList.isEmpty()) {
            MongoConfigurationContributor composite = cfg -> contributorList.forEach(c -> c.configure(cfg));
            properties.put(CONFIGURATION_CONTRIBUTOR_KEY, composite);
        }
        return properties;
    }

    /**
     * Creates the {@link LocalContainerEntityManagerFactoryBean} for MongoDB-backed JPA. Marked {@link Primary} to
     * match Spring Boot and avoid ambiguity if a second {@link EntityManagerFactory} is present (a single persistence
     * unit is the supported model).
     *
     * @param factoryBuilder the entity-manager-factory builder
     * @param applicationContext the application context, used to resolve entity-scan packages
     * @return the configured factory bean
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean({LocalContainerEntityManagerFactoryBean.class, EntityManagerFactory.class})
    // EntityManagerFactoryBuilder only exposes build() via dataSource(...), but MongoDB has no JDBC
    // DataSource. Hibernate connects through jakarta.persistence.jdbc.url. null is the correct
    // "no DataSource" value (LocalContainerEntityManagerFactoryBean tolerates it); NullAway cannot see
    // that the builder's parameter is effectively nullable.
    @SuppressWarnings("NullAway")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder factoryBuilder, ApplicationContext applicationContext) {
        // Prefer packages registered via @EntityScan; fall back to the main @SpringBootApplication
        // package (AutoConfigurationPackages) so entities are still discovered without @EntityScan.
        var packages = EntityScanPackages.get(applicationContext).getPackageNames();
        if (packages.isEmpty()) {
            packages = AutoConfigurationPackages.get(applicationContext);
        }
        return factoryBuilder
                .dataSource(null)
                .packages(packages.toArray(String[]::new))
                .build();
    }

    /**
     * Creates a {@link JpaTransactionManager} bound to the {@link EntityManagerFactory}.
     *
     * @param entityManagerFactory the entity manager factory to manage transactions for
     * @return the configured transaction manager
     */
    @Bean
    @ConditionalOnMissingBean(TransactionManager.class)
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * Borrows the Spring-managed {@link MongoClient} and hands it to Hibernate via a
     * {@link MongoConfigurationContributor}. Gated by {@code @ConditionalOnClass(MongoConnectionDetails.class)} so it is
     * inert without {@code spring-boot-mongodb}, and by {@code @ConditionalOnBean(MongoClient.class)} so it borrows only
     * an existing client.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MongoConnectionDetails.class)
    @ConditionalOnBean(MongoClient.class)
    @ConditionalOnMissingBean(MongoConfigurationContributor.class)
    static class MongoClientBridgeConfiguration {

        MongoClientBridgeConfiguration() {}

        /**
         * Ordered {@link Ordered#HIGHEST_PRECEDENCE} so it is applied first in the contributor composite, letting a user
         * {@link MongoConfigurationContributor} (default order) override the supplied database name.
         *
         * @param mongoClient the Spring-managed client to borrow
         * @param connectionDetails the connection details used to resolve the database name from the connection string
         * @param mongoProperties the bound {@code spring.mongodb.*} properties used to resolve the database name
         * @return a contributor that hands Hibernate the borrowed client and resolved database name
         */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        MongoConfigurationContributor mongoClientConfigurationContributor(
                MongoClient mongoClient, MongoConnectionDetails connectionDetails, MongoProperties mongoProperties) {
            var resolved = mongoProperties.getDatabase();
            if (resolved == null) {
                resolved = connectionDetails.getConnectionString().getDatabase();
            }
            if (resolved == null) {
                throw new IllegalStateException(
                        "A MongoDB database name is required for spring.jpa.database-platform=MongoDB but none was found. "
                                + "Put it in the connection string (spring.mongodb.uri=mongodb://host/<db>), set "
                                + "spring.mongodb.database, or set it via MongoConfigurationContributor.databaseName(...).");
            }
            var databaseName = resolved;
            return configurator -> configurator.mongoClient(mongoClient).databaseName(databaseName);
        }
    }

    /**
     * Registers an {@link OpenEntityManagerInViewInterceptor} in servlet web applications, mirroring Spring Boot's
     * Open-Session-in-View support so {@code spring.jpa.open-in-view} behaves identically here.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer.class)
    @ConditionalOnMissingBean(OpenEntityManagerInViewInterceptor.class)
    @ConditionalOnBooleanProperty(name = "spring.jpa.open-in-view", matchIfMissing = true)
    public static class MongoJpaWebConfiguration {

        private static final Log logger = LogFactory.getLog(MongoJpaWebConfiguration.class);

        private final JpaProperties jpaProperties;

        MongoJpaWebConfiguration(JpaProperties jpaProperties) {
            this.jpaProperties = jpaProperties;
        }

        /**
         * Registers an {@link OpenEntityManagerInViewInterceptor} for MongoDB-backed JPA.
         *
         * @return the interceptor
         */
        @Bean
        public OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor() {
            if (jpaProperties.getOpenInView() == null) {
                logger.warn("spring.jpa.open-in-view is enabled by default. Therefore, database queries may be "
                        + "performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable "
                        + "this warning");
            }
            return new OpenEntityManagerInViewInterceptor();
        }

        /**
         * Registers the {@link OpenEntityManagerInViewInterceptor} as a Spring MVC interceptor.
         *
         * @param interceptor the interceptor to register
         * @return a {@link WebMvcConfigurer} that adds the interceptor
         */
        @Bean
        public WebMvcConfigurer mongoOpenEntityManagerInViewInterceptorConfigurer(
                OpenEntityManagerInViewInterceptor interceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addWebRequestInterceptor(interceptor);
                }
            };
        }
    }

    /**
     * Registers reflection hints for the naming-strategy and {@code NoJtaPlatform} types Hibernate instantiates
     * reflectively, mirroring Spring Boot's {@code HibernateRuntimeHints} so this configuration works in a native image.
     */
    static class MongoHibernateRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            hints.reflection()
                    .registerType(PhysicalNamingStrategyStandardImpl.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
                    .registerType(
                            ImplicitNamingStrategyJpaCompliantImpl.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            for (var noJtaPlatform : List.of(
                    "org.hibernate.engine.transaction.jta.platform.internal.NoJtaPlatform",
                    "org.hibernate.service.jta.platform.internal.NoJtaPlatform")) {
                hints.reflection()
                        .registerType(TypeReference.of(noJtaPlatform), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            }
        }
    }
}

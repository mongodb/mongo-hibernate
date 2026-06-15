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

package com.mongodb.hibernate.junit;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.ServiceRegistryProducer;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Implements {@link ServiceRegistryProducer} for MongoDB integration tests.
 *
 * <p>Hibernate's testing framework ({@code ServiceRegistryUtil.serviceRegistryBuilder}) injects
 * {@code SharedDriverManagerConnectionProvider} and {@code CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT=true} into the
 * builder before
 * {@link com.mongodb.hibernate.internal.service.StandardServiceRegistryScopedState.ServiceContributor#contribute} runs.
 * We remove {@code CONNECTION_PROVIDER} (so {@code contribute()} sees null and autoconfigures cleanly) and clear the
 * autocommit flag here.
 */
public interface MongoServiceRegistryProducer extends ServiceRegistryProducer {

    @Override
    default StandardServiceRegistry produceServiceRegistry(StandardServiceRegistryBuilder ssrb) {
        AnnotationSupport.findAnnotation(getClass(), ServiceRegistry.class).ifPresent(ann -> {
            for (var setting : ann.settings()) {
                ssrb.applySetting(setting.name(), setting.value());
            }
        });
        // Note that StandardServiceRegistryBuilder#getSettings is an internal API
        ssrb.getSettings().remove(AvailableSettings.CONNECTION_PROVIDER);
        ssrb.applySetting(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, false);
        return ssrb.build();
    }
}

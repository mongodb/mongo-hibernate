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

package com.mongodb.hibernate.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mongodb.event.ClusterClosedEvent;
import com.mongodb.event.ClusterListener;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class MongoConfigurationContributorTests {
    @Test
    void takesEffect() {
        var clusterListener = new TestClusterListener();
        buildSessionFactory(configurator -> configurator.applyToMongoClientSettings(
                        clientSettingsBuilder -> clientSettingsBuilder.applyToClusterSettings(
                                clusterSettingsBuilder -> clusterSettingsBuilder.addClusterListener(clusterListener))))
                .close();
        clusterListener.assertClusterClosed();
    }

    @Test
    void exceptionPropagates() {
        RuntimeException expected = new RuntimeException();
        HibernateException actual = assertThrows(HibernateException.class, () -> {
            buildSessionFactory(configurator -> {
                        throw expected;
                    })
                    .close();
        });
        assertSame(expected, actual.getCause());
    }

    private SessionFactory buildSessionFactory(MongoConfigurationContributor mongoConfigurationContributor) {
        return new MetadataSources(new StandardServiceRegistryBuilder()
                        .addService(MongoConfigurationContributor.class, mongoConfigurationContributor)
                        .build())
                .getMetadataBuilder()
                .build()
                .getSessionFactoryBuilder()
                .build();
    }

    private static class TestClusterListener implements ClusterListener {
        private static final Duration TIMEOUT = Duration.ofSeconds(5);

        private final CompletableFuture<ClusterClosedEvent> closedEvent;

        TestClusterListener() {
            closedEvent = new CompletableFuture<>();
        }

        @Override
        public void clusterClosed(ClusterClosedEvent event) {
            assertTrue(closedEvent.complete(event));
        }

        void assertClusterClosed() {
            assertDone(closedEvent);
        }

        private static void assertDone(Future<?> future) {
            try {
                future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (TimeoutException e) {
                fail(e);
            }
        }
    }
}

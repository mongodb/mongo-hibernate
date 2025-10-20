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

package com.mongodb.hibernate.type.temporal;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;

import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.hibernate.testing.orm.transaction.TransactionUtil;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            InstantIntegrationTests.Item.class,
        })
@ExtendWith(MongoExtension.class)
class InstantIntegrationTests implements SessionFactoryScopeAware {

    private static final TimeZone ORIGINAL_JVM_TIMEZONE = TimeZone.getDefault();
    private static final String ZONE_OFFSET = "+11:13";
    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    private static Stream<Arguments> differentTimeZones() {
        return Stream.of(
                Arguments.of(ZoneId.of("Etc/GMT+1"), ZoneId.of("Etc/UTC")),
                Arguments.of(ZoneId.of("Etc/GMT-1"), ZoneId.of("Etc/GMT+2")));
    }

    private static Stream<Arguments> instantPersistAndReadParameters() {
        return differentTimeZones().flatMap(arguments -> {
            var tz0 = (ZoneId) arguments.get()[0];
            var tz1 = (ZoneId) arguments.get()[1];
            return Stream.of(
                    Arguments.of(
                            tz0,
                            tz1,
                            // Value to save.
                            // We support milliseconds precision, so nanoseconds are rounded down to milliseconds.
                            Instant.parse("2007-12-03T10:15:30.002900000Z"),
                            // Expected value.
                            Instant.parse("2007-12-03T10:15:30.002000000Z")),
                    Arguments.of(
                            tz0, tz1, Instant.parse("1500-12-03T10:15:30Z"), Instant.parse("1500-12-03T10:15:30Z")),
                    Arguments.of(
                            tz0,
                            tz1,
                            Instant.parse("-000001-12-03T10:15:30Z"),
                            Instant.parse("-000001-12-03T10:15:30Z")));
        });
    }

    @ParameterizedTest(name = "testRoundTripSessionTzsEqual: Write(sys={0}, sess={1}). Read(sys={0}, sess={1})")
    @MethodSource("instantPersistAndReadParameters")
    void testRoundTripSessionTzsEqual(ZoneId systemDefaultTimeZone, ZoneId jdbcTimeZone, Instant toSave, Instant toRead)
            throws Exception {
        var instantItem = new Item(1, toSave);
        withSystemTimeZone(
                systemDefaultTimeZone, () -> inTransaction(jdbcTimeZone, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                systemDefaultTimeZone,
                () -> fromTransaction(jdbcTimeZone, session -> session.find(Item.class, instantItem.id)));

        var expectedItem = new Item(1, toRead);
        assertEq(expectedItem, loadedInstantItem);
    }

    @ParameterizedTest(
            name = "testRoundTripWriteAndReadPathTzsNotEqual: Write(sys={0}, sess={0}). Read(sys={1}, sess={1})")
    @MethodSource("instantPersistAndReadParameters")
    void testRoundTripWriteAndReadPathTzsNotEqual(
            ZoneId writeTimeZonePath, ZoneId readTimeZonePath, Instant toSave, Instant toRead) throws Exception {
        var instantItem = new Item(1, toSave);
        withSystemTimeZone(
                writeTimeZonePath, () -> inTransaction(writeTimeZonePath, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                readTimeZonePath,
                () -> fromTransaction(readTimeZonePath, session -> session.find(Item.class, instantItem.id)));

        var expectedItem = new Item(1, toRead);
        assertEq(expectedItem, loadedInstantItem);
    }

    @ParameterizedTest(
            name = "testRoundTripSessionTzsNotEqual:" + "Write(sys=" + ZONE_OFFSET + ", sess={0}). Read(sys="
                    + ZONE_OFFSET + ", sess={1})")
    @MethodSource("instantPersistAndReadParameters")
    void testRoundTripSessionTzsNotEqual(
            ZoneId sessionWriteTimeZone, ZoneId sessionReadTimeZone, Instant toSave, Instant toRead) throws Exception {
        var systemTimeZone = ZoneId.of(ZONE_OFFSET);
        var instantItem = new Item(1, toSave);

        withSystemTimeZone(
                systemTimeZone, () -> inTransaction(sessionWriteTimeZone, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                systemTimeZone,
                () -> fromTransaction(sessionReadTimeZone, session -> session.find(Item.class, instantItem.id)));

        var expectedItem = new Item(1, toRead);
        assertEq(expectedItem, loadedInstantItem);
    }

    @Entity
    @Table(name = Item.COLLECTION_NAME)
    static class Item {
        private static final String COLLECTION_NAME = "items";

        @Id
        int id;

        Instant instant;
        Collection<Instant> instantCollection;
        Instant[] instants;
        AggregateEmbeddable aggregateEmbeddable;
        FlattenedEmbeddable flattenedEmbeddable;

        Item() {}

        Item(int id, Instant instant) {
            this.id = id;
            this.instant = instant;
            this.instantCollection = List.of(instant, instant);
            this.instants = instantCollection.toArray(new Instant[] {});
            this.aggregateEmbeddable = new AggregateEmbeddable(instant);
            this.flattenedEmbeddable = new FlattenedEmbeddable(instant);
        }
    }

    @Embeddable
    static class FlattenedEmbeddable {
        Instant flattenedInstant;

        FlattenedEmbeddable() {}

        FlattenedEmbeddable(Instant instant) {
            flattenedInstant = instant;
        }
    }

    @Embeddable
    @Struct(name = "AggregateEmbeddable")
    static class AggregateEmbeddable {
        Instant instant;

        AggregateEmbeddable() {}

        AggregateEmbeddable(Instant instant) {
            this.instant = instant;
        }
    }

    private void inTransaction(ZoneId tz, Consumer<EntityManager> action) {
        var sessionFactoryImpl = sessionFactoryScope.getSessionFactory();
        try (var sessionWithTimeZone = sessionFactoryImpl
                .withOptions()
                .jdbcTimeZone(TimeZone.getTimeZone(tz))
                .openSession()) {
            TransactionUtil.inTransaction(sessionWithTimeZone, action);
        }
    }

    private <R> R fromTransaction(ZoneId tz, Function<EntityManager, R> action) {
        var sessionFactoryImpl = sessionFactoryScope.getSessionFactory();
        try (var sessionWithTimeZone = sessionFactoryImpl
                .withOptions()
                .jdbcTimeZone(TimeZone.getTimeZone(tz))
                .openSession()) {
            return TransactionUtil.fromTransaction(sessionWithTimeZone, action);
        }
    }

    private static void withSystemTimeZone(ZoneId tz, Runnable runnable) {
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(tz));
            runnable.run();
        } finally {
            TimeZone.setDefault(ORIGINAL_JVM_TIMEZONE);
        }
    }

    private static <T> T withSystemTimeZone(ZoneId tz, Callable<T> callable) throws Exception {
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(tz));
            return callable.call();
        } finally {
            TimeZone.setDefault(ORIGINAL_JVM_TIMEZONE);
        }
    }
}

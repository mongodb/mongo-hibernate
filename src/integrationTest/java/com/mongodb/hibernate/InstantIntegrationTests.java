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

package com.mongodb.hibernate;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;

import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.hibernate.annotations.Struct;
import org.hibernate.engine.spi.SessionFactoryImplementor;
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

    private static final TimeZone CURRENT_JVM_TIMEZONE = TimeZone.getDefault();
    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    public static Stream<Arguments> differentTimeZones() {
        return Stream.of(
                Arguments.of(TimeZone.getTimeZone("UTC"), TimeZone.getTimeZone("GMT+1")),
                Arguments.of(TimeZone.getTimeZone("GMT+1"), TimeZone.getTimeZone("GMT+1")),
                Arguments.of(TimeZone.getTimeZone("GMT+1"), TimeZone.getTimeZone("GMT+2")),
                Arguments.of(TimeZone.getTimeZone("GMT+05:31"), TimeZone.getTimeZone("GMT+05:45")),
                Arguments.of(TimeZone.getTimeZone("America/New_York"), TimeZone.getTimeZone("America/Los_Angeles")));
    }

    /**
     * Hibernate ORM will use TIMESTAMP_UTC Sql type by default (it is Hibernate ORM defined type, not JDBC). This means
     * it will be treated as TIMESTAMP at JDBC level, with Calendar being UTC.
     *
     * <p>For array/collection element values and for @Struct/@Embeddable component attributes the same Instant is
     * propagated unchanged: each Instant is stored similarly to TIMESTAMP_UTC semantics
     */
    public static Stream<Arguments> instantPersistAndReadParameters() {
        return differentTimeZones().flatMap(arguments -> {
            TimeZone systemDefaultTimeZone = (TimeZone) arguments.get()[0];
            TimeZone jdbcTimezone = (TimeZone) arguments.get()[0];
            return Stream.of(
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            // Attribute or an element of an attribute to save.
                            Instant.parse("2007-12-03T10:15:30.00Z"),
                            // Expected attribute or an element of an attribute after read.
                            Instant.parse("2007-12-03T10:15:30.00Z")),
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            Instant.parse("1500-12-03T10:15:30Z"),
                            Instant.parse("1500-12-03T10:15:30Z")),
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            Instant.parse("-000001-12-03T10:15:30Z"),
                            Instant.parse("-000001-12-03T10:15:30Z")),
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            // Other dialects might support nanoseconds precision, however, in our case the precision is
                            // to within milliseconds.
                            Instant.parse("2007-12-03T10:15:30.000000999Z"),
                            Instant.parse("2007-12-03T10:15:30Z")),
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            // We support milliseconds precision, so nanoseconds are rounded down to milliseconds.
                            Instant.parse("2007-12-03T10:15:30.002900000Z"),
                            Instant.parse("2007-12-03T10:15:30.002000000Z")));
        });
    }

    /** Write: system tz: T1, session tz: T2 Read: system tz: T1, session tz: T2 */
    @ParameterizedTest(
            name =
                    "Instant: system TZ equal per read/write; system TZ not equal session TZ; session TZ equal per read/write;"
                            + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("instantPersistAndReadParameters")
    void testInstantRoundTripWhenSessionTzEqual(
            TimeZone systemDefaultTimeZone, TimeZone jdbcTimeZone, Instant toSave, Instant toRead) throws Exception {
        var instantItem = new Item(1, toSave);
        withSystemTimeZone(
                systemDefaultTimeZone, () -> inTransaction(jdbcTimeZone, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                systemDefaultTimeZone,
                () -> fromTransaction(jdbcTimeZone, session -> session.find(Item.class, instantItem.id)));

        var expectedItem = new Item(1, toRead);
        assertEq(expectedItem, loadedInstantItem);
    }

    /** Write: system tz: T1, session tz: T1 Read: system tz: T2, session tz: T2 */
    @ParameterizedTest(
            name = "Instant: system TZ differ per read/write; session TZ equals system TZ;"
                    + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("instantPersistAndReadParameters")
    void testInstantRoundTripWhenSessionTzNotEqual(
            TimeZone writeTimeZonePath, TimeZone readTimeZonePath, Instant toSave, Instant toRead) throws Exception {
        var instantItem = new Item(1, toSave);
        withSystemTimeZone(
                writeTimeZonePath, () -> inTransaction(writeTimeZonePath, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                readTimeZonePath,
                () -> fromTransaction(readTimeZonePath, session -> session.find(Item.class, instantItem.id)));

        var expectedItem = new Item(1, toRead);
        assertEq(expectedItem, loadedInstantItem);
    }

    /** Write: system tz: T1, session tz: T2 Read: system tz: T1, session tz: T3 */
    @ParameterizedTest(
            name =
                    "Instant:system TZ equal per read/write; system TZ not equal session TZ; session TZ not equal per read/write;"
                            + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("instantPersistAndReadParameters")
    void testInstantPersistAndReadDifferentTimeZones(
            TimeZone writeTimeZone, TimeZone readTimeZone, Instant toSave, Instant toRead) throws Exception {
        TimeZone systemTimeZone = TimeZone.getTimeZone("UTC+10");
        var instantItem = new Item(1, toSave);

        withSystemTimeZone(systemTimeZone, () -> inTransaction(writeTimeZone, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                systemTimeZone,
                () -> fromTransaction(readTimeZone, session -> session.find(Item.class, instantItem.id)));

        var expectedItem = new Item(1, toRead);
        assertEq(expectedItem, loadedInstantItem);
    }

    @Entity(name = "Item")
    @Table(name = Item.COLLECTION_NAME)
    static class Item {
        private static final String COLLECTION_NAME = "items";

        @Id
        int id;

        Instant instant;
        Collection<Instant> instantCollection;
        Instant[] instants;
        AggregateEmbeddable aggregateEmbeddable;
        InlinedEmbeddable inlinedEmbeddable;

        public Item() {}

        public Item(int id, Instant instant) {
            this.id = id;
            this.instant = instant;
            this.instantCollection = List.of(instant, instant);
            this.instants = instantCollection.toArray(new Instant[] {});
            this.aggregateEmbeddable = new AggregateEmbeddable(instant);
            this.inlinedEmbeddable = new InlinedEmbeddable(instant);
        }
    }

    @Embeddable
    static class InlinedEmbeddable {
        public Instant instantEmbeddable;

        public InlinedEmbeddable() {}

        public InlinedEmbeddable(Instant instantEmbeddable) {
            this.instantEmbeddable = instantEmbeddable;
        }
    }

    @Embeddable
    @Struct(name = "aggregate_embeddable")
    static class AggregateEmbeddable {
        public Instant instant;

        public AggregateEmbeddable() {}

        public AggregateEmbeddable(Instant instant) {
            this.instant = instant;
        }
    }

    public void inTransaction(TimeZone timeZone, Consumer<EntityManager> action) {
        SessionFactoryImplementor sessionFactoryImpl = sessionFactoryScope.getSessionFactory();
        try (Session sessionWithTimeZone =
                sessionFactoryImpl.withOptions().jdbcTimeZone(timeZone).openSession()) {
            TransactionUtil.inTransaction(sessionWithTimeZone, action);
        }
    }

    public <R> R fromTransaction(TimeZone timeZone, Function<EntityManager, R> action) {
        SessionFactoryImplementor sessionFactoryImpl = sessionFactoryScope.getSessionFactory();
        try (Session sessionWithTimeZone =
                sessionFactoryImpl.withOptions().jdbcTimeZone(timeZone).openSession()) {
            return TransactionUtil.fromTransaction(sessionWithTimeZone, action);
        }
    }

    private static void withSystemTimeZone(TimeZone timeZone, Runnable runnable) {
        try {
            TimeZone.setDefault(timeZone);
            runnable.run();
        } finally {
            TimeZone.setDefault(CURRENT_JVM_TIMEZONE);
        }
    }

    private static <T> T withSystemTimeZone(TimeZone timeZone, Callable<T> callable) throws Exception {
        try {
            TimeZone.setDefault(timeZone);
            return callable.call();
        } finally {
            TimeZone.setDefault(CURRENT_JVM_TIMEZONE);
        }
    }
}

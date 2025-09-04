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

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.hibernate.testing.orm.transaction.TransactionUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.mongodb.hibernate.MongoTestAssertions.assertUsingRecursiveComparison;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            TemporalIntegrationTests.ItemLocalDate.class,
            TemporalIntegrationTests.ItemLocalTime.class,
            TemporalIntegrationTests.ItemLocalDateTime.class,
            TemporalIntegrationTests.ItemInstant.class,
            TemporalIntegrationTests.ItemOffsetTime.class,
            TemporalIntegrationTests.ItemOffsetDateTime.class,
            TemporalIntegrationTests.ItemDate.class
        })
@ServiceRegistry(
//        settings = {
//                @Setting(
//                        name = "hibernate.type.preferred_instant_jdbc_type",
//                        value = "INSTANT"),
//        }
)
@ExtendWith(MongoExtension.class)
public class TemporalIntegrationTests implements SessionFactoryScopeAware {

    private static final TimeZone CURRENT_JVM_TIMEZONE = TimeZone.getDefault();

    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @BeforeAll
    static void setUp() {
        // Overridden to reproduce tests deterministically across different environments.
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+1"));
    }

    @BeforeAll
    static void tearDown() {
        TimeZone.setDefault(CURRENT_JVM_TIMEZONE);
    }

    public static java.util.stream.Stream<Arguments> differentTimeZones() {
        return java.util.stream.Stream.of(
                Arguments.of(TimeZone.getTimeZone("UTC"), TimeZone.getTimeZone("GMT+1")),
                Arguments.of(TimeZone.getTimeZone("GMT+1"), TimeZone.getTimeZone("GMT+1")),
                Arguments.of(TimeZone.getTimeZone("GMT+1"), TimeZone.getTimeZone("GMT+2")),
                Arguments.of(TimeZone.getTimeZone("GMT+05:31"), TimeZone.getTimeZone("GMT+05:45")),
                Arguments.of(TimeZone.getTimeZone("America/New_York"), TimeZone.getTimeZone("America/Los_Angeles")));
    }

    public static Stream<TimeZone> timeZoneStream() {
        return java.util.stream.Stream.of(
                TimeZone.getDefault(),
                TimeZone.getTimeZone("UTC"),
                TimeZone.getTimeZone("GMT+05:31"),
                TimeZone.getTimeZone("GMT+05:45"),
                TimeZone.getTimeZone("GMT+1"),
                TimeZone.getTimeZone("GMT-1"),
                TimeZone.getTimeZone("GMT-05:45"),
                TimeZone.getTimeZone("GMT+05:31"),
                TimeZone.getTimeZone("America/New_York"));
    }

    @ParameterizedTest(
            name =
                    "LocalDate: system TZ equal per read/write; system TZ not equal session TZ; session TZ equal per read/write;"
                            + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T2
     system tz: T1 <- session tz: T2
    */
    void testLocalDateRoundTripWhenSessionTzEqual(TimeZone systemDefaultTimeZone, TimeZone jdbcTimeZone)
            throws Exception {
        var localDateItem = new ItemLocalDate(1, LocalDate.of(2025, 1, 1));
        withSystemTimeZone(
                systemDefaultTimeZone, () -> inTransaction(jdbcTimeZone, session -> session.persist(localDateItem)));
        var loadedLocalDateItem = withSystemTimeZone(
                systemDefaultTimeZone,
                () -> fromTransaction(jdbcTimeZone, session -> session.find(ItemLocalDate.class, localDateItem.id)));
        assertUsingRecursiveComparison(localDateItem, loadedLocalDateItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest(
            name = "LocalDate: system TZ differ per read/write; session TZ equals system TZ;"
                    + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T1
     system tz: T2 <- session tz: T2
    */
    void testLocalDateRoundTripWhenSessionTzNotEqual(TimeZone writeTimeZonePath, TimeZone readTimeZonePath)
            throws Exception {
        var localDateItem = new ItemLocalDate(1, LocalDate.of(2025, 1, 1));
        withSystemTimeZone(
                writeTimeZonePath, () -> inTransaction(writeTimeZonePath, session -> session.persist(localDateItem)));

        var loadedLocalDateItem = withSystemTimeZone(
                readTimeZonePath,
                () -> fromTransaction(
                        readTimeZonePath, session -> session.find(ItemLocalDate.class, localDateItem.id)));
        assertUsingRecursiveComparison(localDateItem, loadedLocalDateItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T2
     system tz: T1 <- session tz: T3
    */
    void testLocalDateRoundTripMismatchedTimeZones(TimeZone writeTimeZone, TimeZone readTimeZone) throws Exception {
        TimeZone systemTimeZone = TimeZone.getTimeZone("UTC+10");
        var localDateItem = new ItemLocalDate(1, LocalDate.of(2025, 1, 1));
        withSystemTimeZone(
                systemTimeZone, () -> inTransaction(writeTimeZone, session -> session.persist(localDateItem)));

        var loadedLocalDateItem = withSystemTimeZone(
                systemTimeZone,
                () -> fromTransaction(readTimeZone, session -> session.find(ItemLocalDate.class, localDateItem.id)));

        assertUsingRecursiveComparison(localDateItem, loadedLocalDateItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest(
            name =
                    "LocalTime: system TZ equal per read/write; system TZ not equal session TZ; session TZ equal per read/write;"
                            + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T2
     system tz: T1 <- session tz: T2
    */
    void testLocalTimeRoundTripWhenSessionTzEqual(TimeZone systemDefaultTimeZone, TimeZone jdbcTimeZone)
            throws Exception {
        var localTimeItem = new ItemLocalTime(1, LocalTime.of(3, 15, 30));
        withSystemTimeZone(
                systemDefaultTimeZone, () -> inTransaction(jdbcTimeZone, session -> session.persist(localTimeItem)));
        var loadedLocalTimeItem = withSystemTimeZone(
                systemDefaultTimeZone,
                () -> fromTransaction(jdbcTimeZone, session -> session.find(ItemLocalTime.class, localTimeItem.id)));

        assertUsingRecursiveComparison(localTimeItem, loadedLocalTimeItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest(
            name = "LocalTime: system TZ differ per read/write; session TZ equals system TZ;"
                    + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T1
     system tz: T2 <- session tz: T2
    */
    void testLocalTimeRoundTripWhenSessionTzNotEqual(TimeZone writeTimeZonePath, TimeZone readTimeZonePath)
            throws Exception {
        var localTimeItem = new ItemLocalTime(1, LocalTime.of(3, 15, 30));
        withSystemTimeZone(
                writeTimeZonePath, () -> inTransaction(writeTimeZonePath, session -> session.persist(localTimeItem)));
        var loadedLocalTimeItem = withSystemTimeZone(
                readTimeZonePath,
                () -> fromTransaction(
                        readTimeZonePath, session -> session.find(ItemLocalTime.class, localTimeItem.id)));

        assertUsingRecursiveComparison(localTimeItem, loadedLocalTimeItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T2
     system tz: T1 <- session tz: T3
    */
    void testLocalTimeRoundTripMismatchedTimeZones(TimeZone writeTimeZone, TimeZone readTimeZone) throws Exception {
        TimeZone systemTimeZone = TimeZone.getTimeZone("UTC+1");

        LocalDateTime localDateTimeSavedToDatabase = LocalTime.of(3, 15, 30)
                .atDate(LocalDate.of(1970, 1, 1))
                .atZone(systemTimeZone.toZoneId())
                .toInstant()
                .atZone(writeTimeZone.toZoneId())
                .toLocalDateTime();

        var itemToSave = new ItemLocalTime(1, LocalTime.of(3, 15, 30));
        withSystemTimeZone(systemTimeZone, () -> inTransaction(writeTimeZone, session -> session.persist(itemToSave)));

        LocalTime expectedLocalTimeFromReadInDifferentSessionTimeZone = localDateTimeSavedToDatabase
                .atZone(readTimeZone.toZoneId())
                .toInstant()
                .atZone(systemTimeZone.toZoneId())
                .toLocalDateTime()
                .toLocalTime();

        itemToSave.localTime = expectedLocalTimeFromReadInDifferentSessionTimeZone;
        var loadedLocalTimeItem = withSystemTimeZone(
                systemTimeZone,
                () -> fromTransaction(readTimeZone, session -> session.find(ItemLocalTime.class, itemToSave.id)));

        assertUsingRecursiveComparison(itemToSave, loadedLocalTimeItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest(
            name =
                    "LocalDateTime: system TZ equal per read/write; system TZ not equal session TZ; session TZ equal per read/write;"
                            + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T2
     system tz: T1 <- session tz: T2
    */
    void testLocalDateTimeRoundTripWhenSessionTzEqual(TimeZone systemDefaultTimeZone, TimeZone jdbcTimeZone)
            throws Exception {
        var localDateTimeItem = new ItemLocalDateTime(1, LocalDateTime.of(2025, 10, 10, 3, 15, 30));
        withSystemTimeZone(
                systemDefaultTimeZone,
                () -> inTransaction(jdbcTimeZone, session -> session.persist(localDateTimeItem)));
        var loadedLocalDateTimeItem = withSystemTimeZone(
                systemDefaultTimeZone,
                () -> fromTransaction(
                        jdbcTimeZone, session -> session.find(ItemLocalDateTime.class, localDateTimeItem.id)));

        assertUsingRecursiveComparison(
                localDateTimeItem, loadedLocalDateTimeItem, (recursiveComparisonAssert, expected) -> {
                    recursiveComparisonAssert
                            .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                            .isEqualTo(expected);
                });
    }

    @ParameterizedTest(
            name = "LocalDateTime: system TZ differ per read/write; session TZ equals system TZ;"
                    + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T1
     system tz: T2 <- session tz: T2
    */
    void testLocalDateTimeRoundTripWhenSessionTzNotEqual(TimeZone writeTimeZonePath, TimeZone readTimeZonePath)
            throws Exception {
        var localDateTimeItem = new ItemLocalDateTime(1, LocalDateTime.of(2025, 10, 10, 3, 15, 30));
        withSystemTimeZone(
                writeTimeZonePath,
                () -> inTransaction(writeTimeZonePath, session -> session.persist(localDateTimeItem)));
        var loadedLocalDateTimeItem = withSystemTimeZone(
                readTimeZonePath,
                () -> fromTransaction(
                        readTimeZonePath, session -> session.find(ItemLocalDateTime.class, localDateTimeItem.id)));

        assertUsingRecursiveComparison(
                localDateTimeItem, loadedLocalDateTimeItem, (recursiveComparisonAssert, expected) -> {
                    recursiveComparisonAssert
                            .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                            .isEqualTo(expected);
                });
    }

    @ParameterizedTest
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T2
     system tz: T1 <- session tz: T3
    */
    void testLocalDateTimeRoundTripMismatchedTimeZones(TimeZone writeTimeZone, TimeZone readTimeZone) {
        LocalDateTime localDateTimeToSave = LocalDateTime.of(2025, 10, 10, 3, 15, 30);
        LocalDateTime expectedLocalDateTimeSavedToDatabase = localDateTimeToSave
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .atZone(writeTimeZone.toZoneId())
                .toLocalDateTime();

        var localDateTimeItem = new ItemLocalDateTime(1, localDateTimeToSave);
        inTransaction(writeTimeZone, session -> session.persist(localDateTimeItem));

        LocalDateTime expectedLocalTimeFromReadInDifferentSessionTimeZone = expectedLocalDateTimeSavedToDatabase
                .atZone(readTimeZone.toZoneId())
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        localDateTimeItem.localDateTime = expectedLocalTimeFromReadInDifferentSessionTimeZone;
        var loadedLocalTimeItem =
                fromTransaction(readTimeZone, session -> session.find(ItemLocalDateTime.class, localDateTimeItem.id));

        assertUsingRecursiveComparison(
                localDateTimeItem, loadedLocalTimeItem, (recursiveComparisonAssert, expected) -> {
                    recursiveComparisonAssert
                            .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                            .isEqualTo(expected);
                });
    }

    public static Stream<Arguments> testInstantPersistAndRead() {
        /*
         * Hibernate will use TIMESTAMP_UTC Sql type (it is HIBERNATE defined type, not JDBC). This means it will be treated as TIMESTAMP
         * at JDBC level, with Calendar being UTC.
         */
        return differentTimeZones().flatMap(arguments -> {
            TimeZone systemDefaultTimeZone = (TimeZone) arguments.get()[0];
            TimeZone jdbcTimezone = (TimeZone) arguments.get()[0];
            return Stream.of(
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            Instant.parse("2007-12-03T10:15:30.00Z"), // set in entity to save
                            Instant.parse("2007-12-03T10:15:30.00Z")), // expected in entity when read
                    // nanoseconds are ignored on write.
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            /*
                               Postgres for example supports only nanosecond precision. However, in our case the precision is within
                               milliseconds.
                            */
                            Instant.parse("2007-12-03T10:15:30Z").plusNanos(999),
                            Instant.parse("2007-12-03T10:15:30Z")),
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            /*
                              Postgres for example supports only nanosecond precision. However, in our case the precision is within
                               milliseconds.
                               We support milliseconds precision, so nanoseconds should be rounded down to milliseconds.
                            */
                            Instant.parse("2007-12-03T10:15:30Z").plusNanos(2900000),
                            Instant.parse("2007-12-03T10:15:30Z").plusNanos(2000000)));
        });
    }

    @ParameterizedTest(
            name =
                    "Instant: system TZ equal per read/write; system TZ not equal session TZ; session TZ equal per read/write;"
                            + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("testInstantPersistAndRead")
    /*
     system tz: T1 -> session tz: T2
     system tz: T1 <- session tz: T2
    */
    void testInstantRoundTripWhenSessionTzEqual(
            TimeZone systemDefaultTimeZone, TimeZone jdbcTimeZone, Instant toSave, Instant toRead) throws Exception {
        var instantItem = new ItemInstant(1, toSave);
        withSystemTimeZone(
                systemDefaultTimeZone, () -> inTransaction(jdbcTimeZone, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                systemDefaultTimeZone,
                () -> fromTransaction(jdbcTimeZone, session -> session.find(ItemInstant.class, instantItem.id)));

        instantItem.instant = toRead;
        assertUsingRecursiveComparison(instantItem, loadedInstantItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest(
            name = "Instant: system TZ differ per read/write; session TZ equals system TZ;"
                    + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("testInstantPersistAndRead")
    /*
     system tz: T1 -> session tz: T1
     system tz: T2 <- session tz: T2
    */
    void testInstantRoundTripWhenSessionTzNotEqual(
            TimeZone writeTimeZonePath, TimeZone readTimeZonePath, Instant toSave, Instant toRead) throws Exception {
        var instantItem = new ItemInstant(1, toSave);
        withSystemTimeZone(
                writeTimeZonePath, () -> inTransaction(writeTimeZonePath, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                readTimeZonePath,
                () -> fromTransaction(readTimeZonePath, session -> session.find(ItemInstant.class, instantItem.id)));

        instantItem.instant = toRead;
        assertUsingRecursiveComparison(instantItem, loadedInstantItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest
    @MethodSource("testInstantPersistAndRead")
    /*
     system tz: T1 -> session tz: T2
     system tz: T1 <- session tz: T3
    */
    void testInstantPersistAndReadDifferentTimeZones(
            TimeZone writeTimeZone, TimeZone readTimeZone, Instant toSave, Instant toRead) throws Exception {
        TimeZone systemTimeZone = TimeZone.getTimeZone("UTC+10");
        var instantItem = new ItemInstant(1, toSave);

        withSystemTimeZone(systemTimeZone, () -> inTransaction(writeTimeZone, session -> session.persist(instantItem)));
        var loadedInstantItem = withSystemTimeZone(
                systemTimeZone,
                () -> fromTransaction(readTimeZone, session -> session.find(ItemInstant.class, instantItem.id)));

        instantItem.instant = toRead;
        assertUsingRecursiveComparison(instantItem, loadedInstantItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    public static Stream<Arguments> testOffsetTimePersistAndRead() {
        /*
         * Hibernate ORM will normalize OffsetTime to UTC when passing java.sql.Time to the JDBC driver, with Calendar set as UTC.
         * The same approach is applicable to read, the offset will be read as UTC without any offset applied to current JVM timezone, nor
         * session timezone - it is effectively ignored.
         */
        return differentTimeZones().flatMap(arguments -> {
            TimeZone systemDefaultTimeZone = (TimeZone) arguments.get()[0];
            TimeZone jdbcTimezone = (TimeZone) arguments.get()[0];
            return Stream.of(
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            OffsetTime.parse("03:15:30+01:00"), // set in entity to save
                            OffsetTime.parse("02:15:30Z")), // expected in entity when read
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            OffsetTime.parse("00:15:30+01:00"),
                            OffsetTime.parse("23:15:30Z")),
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            OffsetTime.parse("23:15:30-01:00"),
                            OffsetTime.parse("00:15:30Z")),
                    // Nanoseconds are ignored on write in Hibernate ORM TimeUtcAsJdbcTimeJdbcType.
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            OffsetTime.parse("03:15:30Z").withNano(2900000),
                            OffsetTime.parse("03:15:30Z")));
        });
    }

    @ParameterizedTest(
            name =
                    "OffsetTime: system TZ equal per read/write; system TZ not equal session TZ; session TZ equal per read/write;"
                            + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("testOffsetTimePersistAndRead")
    void testOffsetTimeRoundTripWhenSessionTzEqual(
            TimeZone systemDefaultTimeZone, TimeZone jdbcTimeZone, OffsetTime offsetTimeToNormalize, OffsetTime toRead)
            throws Exception {
        /*
         When persisting to the database, JDBC timestamp is normalized
         to the org.hibernate.cfg.AvailableSettings.JDBC_TIME_ZONE (which is specified via session in the test)
         or to the JVM default time zone if not set.
         see TimeZoneStorageType#NORMALIZE
        */
        var offsetTimeItem = new ItemOffsetTime(1, offsetTimeToNormalize);

        /*
          Does not preserve the time zone, and instead normalizes timestamps to UTC.
          see TimeZoneStorageType#NORMALIZE_UTC
        */
        withSystemTimeZone(
                systemDefaultTimeZone, () -> inTransaction(jdbcTimeZone, session -> session.persist(offsetTimeItem)));
        var loadedOffsetTimeItem = withSystemTimeZone(
                systemDefaultTimeZone,
                () -> fromTransaction(jdbcTimeZone, session -> session.find(ItemOffsetTime.class, offsetTimeItem.id)));

        // Expected normalization to UTC on read
        offsetTimeItem.offsetTime = toRead;
        //        /*
        //           Expected normalization to systemDefaultTimeZone on read
        //            There is an unexpected behavior in Hibernate 6.2.7.Final (and some prior versions)
        //            where the OffsetTime is adjusted by the systemDefaultTimeZone offset when reading from the
        // database.
        //         */
        //        long offsetInSeconds = MILLISECONDS.toSeconds(systemDefaultTimeZone.getRawOffset());

        // offsetTimeItem.offsetTime = offsetTime.withOffsetSameInstant(ZoneOffset.ofTotalSeconds((int)
        // offsetInSeconds));
        assertUsingRecursiveComparison(offsetTimeItem, loadedOffsetTimeItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    @ParameterizedTest(
            name = "OffsetTime: system TZ differ per read/write; session TZ equals system TZ;"
                    + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("differentTimeZones")
    /*
     system tz: T1 -> session tz: T1
     system tz: T2 <- session tz: T2
    */
    void testOffsetTimeRoundTripWhenSessionTzNotEqual(TimeZone writeTimeZonePath, TimeZone readTimeZonePath)
            throws Exception {
        int writeTimeZoneOffsetInSeconds = Math.toIntExact(MILLISECONDS.toSeconds(writeTimeZonePath.getRawOffset()));
        int readTimeZoneOffsetInSeconds = Math.toIntExact(MILLISECONDS.toSeconds(readTimeZonePath.getRawOffset()));

        var offsetTimeToNormalize = OffsetTime.parse("03:15:30+01:00");
        var offsetTimeItem = new ItemOffsetTime(1, offsetTimeToNormalize);
        withSystemTimeZone(
                writeTimeZonePath, () -> inTransaction(writeTimeZonePath, session -> session.persist(offsetTimeItem)));
        var loadedOffsetTimeItem = withSystemTimeZone(
                readTimeZonePath,
                () -> fromTransaction(
                        readTimeZonePath, session -> session.find(ItemOffsetTime.class, offsetTimeItem.id)));

        /*
         * When persisting to the database, OffsetTime is normalized to UTC.
         * For example, 03:15:30+01:00 becomes 02:15:30Z.
         * Hibernate then uses the system default time zone, which may have an offset (e.g., +02:00),
         * and treats 02:15:30Z as UTC+2, converting it to UTC again.
         * As a result, 00:15:30 is saved to the database.
         *
         * On read, if the host has a different time zone, the same conversion occurs in reverse.
         * For example, 00:15:30 is read as UTC and then converted to the system default time zone (e.g., +08:00),
         * so Hibernate constructs OffsetTime as 08:15:30 and presets the offset to UTC.
         *
         * The offsetExpected value follows this logic to produce the expected value when read back.
         *
         * NOTE: OffsetDateTime does not have such behaviour, as it's normalization to UTC is straightforward without system timezone being involved.
         */
        offsetTimeItem.offsetTime = offsetTimeToNormalize
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalTime()
                .atOffset(ZoneOffset.ofTotalSeconds(writeTimeZoneOffsetInSeconds))
                .withOffsetSameInstant(ZoneOffset.ofTotalSeconds(readTimeZoneOffsetInSeconds))
                .toLocalTime()
                .atOffset(ZoneOffset.UTC);

        assertUsingRecursiveComparison(offsetTimeItem, loadedOffsetTimeItem, (recursiveComparisonAssert, expected) -> {
            recursiveComparisonAssert
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expected);
        });
    }

    public static Stream<Arguments> testOffsetDateTimePersistAndRead() {
        /*
         * Hibernate will normalize OffsetDateTime to UTC when passing java.sql.Time to the JDBC driver, with Calendar set as UTC.
         * The same approach is applicable to read, the offset will be read as UTC without any offset applied to current JVM timezone, nor
         * session timezone - it is effectively ignored.
         */
        return differentTimeZones().flatMap(arguments -> {
            TimeZone systemDefaultTimeZone = (TimeZone) arguments.get()[0];
            TimeZone jdbcTimezone = (TimeZone) arguments.get()[0];
            return Stream.of(
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            OffsetDateTime.parse("2025-10-10T03:15:30+01:00"), // set in entity to save
                            OffsetDateTime.parse("2025-10-10T02:15:30Z")), // expected in entity when read
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            OffsetDateTime.parse("1970-01-01T00:15:30+01:00"),
                            OffsetDateTime.parse("1969-12-31T23:15:30Z")),
                    // nanoseconds are ignored on write.
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            /*
                               Postgres for example supports only nanosecond precision. However, in our case the precision is within
                               milliseconds.
                            */
                            OffsetDateTime.parse("2025-10-10T03:15:30Z").withNano(999),
                            OffsetDateTime.parse("2025-10-10T03:15:30Z")),
                    Arguments.of(
                            systemDefaultTimeZone,
                            jdbcTimezone,
                            /*
                               We support milliseconds precision, so nanoseconds should be rounded down to milliseconds.
                            */
                            OffsetDateTime.parse("2025-10-10T03:15:30Z").withNano(2900000),
                            OffsetDateTime.parse("2025-10-10T03:15:30Z").withNano(2000000)));
        });
    }

    @ParameterizedTest(
            name =
                    "OffsetDateTime: system TZ equal per read/write; system TZ not equal session TZ; session TZ equal per read/write;"
                            + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("testOffsetDateTimePersistAndRead")
    void testOffsetDateTimeRoundTripWhenSessionTzEqual(
            TimeZone systemDefaultTimeZone, TimeZone jdbcTimeZone, OffsetDateTime toSave, OffsetDateTime toRead)
            throws Exception {
        var offsetDateTimeItem = new ItemOffsetDateTime(1, toSave);
        withSystemTimeZone(
                systemDefaultTimeZone,
                () -> inTransaction(jdbcTimeZone, session -> session.persist(offsetDateTimeItem)));
        var loadedOffsetDateTimeItem = withSystemTimeZone(
                systemDefaultTimeZone,
                () -> fromTransaction(
                        jdbcTimeZone, session -> session.find(ItemOffsetDateTime.class, offsetDateTimeItem.id)));

        // Expected normalization to UTC on read
        offsetDateTimeItem.offsetDateTime = toRead;
        assertUsingRecursiveComparison(
                offsetDateTimeItem, loadedOffsetDateTimeItem, (recursiveComparisonAssert, expected) -> {
                    recursiveComparisonAssert
                            .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                            .isEqualTo(expected);
                });
    }

    @ParameterizedTest(
            name = "OffsetDateTime: system TZ differ per read/write; session TZ equals system TZ;"
                    + "systemDefaultTimeZone={0}, jdbcTimeZone={1}")
    @MethodSource("testOffsetDateTimePersistAndRead")
    void testOffsetDateTimeRoundTripWhenSessionTzNotEqual(
            TimeZone writeTimeZonePath, TimeZone readTimeZonePath, OffsetDateTime toSave, OffsetDateTime toRead)
            throws Exception {
        var offsetDateTimeItem = new ItemOffsetDateTime(1, toSave);
        withSystemTimeZone(
                writeTimeZonePath,
                () -> inTransaction(writeTimeZonePath, session -> session.persist(offsetDateTimeItem)));
        var loadedOffsetDateTimeItem = withSystemTimeZone(
                readTimeZonePath,
                () -> fromTransaction(
                        readTimeZonePath, session -> session.find(ItemOffsetDateTime.class, offsetDateTimeItem.id)));

        // Expected normalization to UTC on read
        offsetDateTimeItem.offsetDateTime = toRead;
        assertUsingRecursiveComparison(
                offsetDateTimeItem, loadedOffsetDateTimeItem, (recursiveComparisonAssert, expected) -> {
                    recursiveComparisonAssert
                            .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                            .isEqualTo(expected);
                });
    }

    @Entity(name = "ItemLocalDate")
    @Table(name = "items_local_date")
    static class ItemLocalDate {
        @Id
        int id;

        LocalDate localDate;

        public ItemLocalDate() {}

        public ItemLocalDate(int id, LocalDate localDate) {
            this.id = id;
            this.localDate = localDate;
        }
    }

    @Entity(name = "ItemLocalTime")
    @Table(name = "items_local_time")
    static class ItemLocalTime {
        @Id
        int id;

        @Column(name = "local_time")
        LocalTime localTime;

        public ItemLocalTime() {}

        public ItemLocalTime(int id, LocalTime localTime) {
            this.id = id;
            this.localTime = localTime;
        }
    }

    @Entity(name = "ItemLocalDateTime")
    @Table(name = "items_local_datetime")
    static class ItemLocalDateTime {
        @Id
        int id;

        LocalDateTime localDateTime;

        public ItemLocalDateTime() {}

        public ItemLocalDateTime(int id, LocalDateTime localDateTime) {
            this.id = id;
            this.localDateTime = localDateTime;
        }
    }

    @Entity(name = "ItemInstant")
    @Table(name = "items_instant")
    static class ItemInstant {
        @Id
        int id;

        Instant instant;

        public ItemInstant() {}

        public ItemInstant(int id, Instant instant) {
            this.id = id;
            this.instant = instant;
        }
    }

    @Entity(name = "ItemOffsetTime")
    @Table(name = "items_offset_time")
    static class ItemOffsetTime {
        @Id
        int id;

        // TODO Lets forbid it
        //        @TimeZoneStorage(TimeZoneStorageType.NORMALIZE)
        //        OffsetTime offsetTime;

        // This is enabled by default, however it is used for explicitness
        @TimeZoneStorage(TimeZoneStorageType.NORMALIZE_UTC)
        OffsetTime offsetTime;

        public ItemOffsetTime() {}

        public ItemOffsetTime(int id, OffsetTime offsetTime) {
            this.id = id;
            this.offsetTime = offsetTime;
        }
    }

    @Entity(name = "ItemOffsetDateTime")
    @Table(name = "items_offset_datetime")
    static class ItemOffsetDateTime {
        @Id
        int id;

        OffsetDateTime offsetDateTime;

        public ItemOffsetDateTime() {}

        public ItemOffsetDateTime(int id, OffsetDateTime offsetDateTime) {
            this.id = id;
            this.offsetDateTime = offsetDateTime;
        }
    }

    @Entity(name = "ItemDate")
    @Table(name = "items_date")
    static class ItemDate {
        @Id
        int id;

        Date date;

        public ItemDate() {}

        public ItemDate(int id, Date date) {
            this.id = id;
            this.date = date;
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

    protected void assertCollectionContainsExactly(String documentAsJsonObject) {
        List<BsonDocument> actualItems = new ArrayList<>();
        mongoCollection.find().into(actualItems);
        for (BsonDocument actualItem : actualItems) {
            System.err.println("JSON " + actualItem.toJson());
        }
        assertThat(actualItems).containsExactly(BsonDocument.parse(documentAsJsonObject));
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

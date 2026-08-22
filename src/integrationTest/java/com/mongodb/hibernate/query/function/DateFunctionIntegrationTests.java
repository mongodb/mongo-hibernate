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

package com.mongodb.hibernate.query.function;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {DateFunctionIntegrationTests.Item.class})
@ExtendWith(MongoExtension.class)
public class DateFunctionIntegrationTests extends AbstractQueryIntegrationTests {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String COLLECTION_NAME = "items";
    private static final Item ITEM = new Item(1, Instant.ofEpochMilli(123456789), Instant.ofEpochMilli(654321987));

    /** Convert a Java (Monday = 1) to Mongo (Sunday = 1) day of the week */
    private static int javaDayOfWeekToMongo(int mondayIsOne) {
        return (mondayIsOne % 7) + 1;
    }

    @SuppressWarnings("unchecked")
    private <T> void assertQueryResult(String hql, T expected, String expectedMql) {
        assertSelectionQuery(
                hql, (Class<T>) expected.getClass(), expectedMql, List.of(expected), Set.of(COLLECTION_NAME));
    }

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(ITEM);
        });
    }

    @ParameterizedTest
    @CsvSource({"yyyy-MM-dd HH:mm:ss,%Y-%m-%d %H:%M:%S"})
    void testFormat(String hqlFormat, String mqlFormat) {
        assertQueryResult(
                "select format(before as '%s') from Item".formatted(hqlFormat),
                ITEM.before.atZone(UTC).format(DateTimeFormatter.ofPattern(hqlFormat)),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dateToString": {
                            "date": "$before",
                            "format": { "$literal": "%s" }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(mqlFormat));
    }

    @Test
    void testExtractSecond() {
        assertQueryResult(
                "select extract(second from before) from Item",
                (float) (ITEM.before.atZone(UTC).getSecond()
                        + ITEM.before.atZone(UTC).getNano() / 1e9),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$add": [
                                {
                                  "$second": "$$time"
                                },
                                {
                                  "$divide": [
                                    {
                                      "$millisecond": "$$time"
                                    },
                                    {
                                      "$literal": 1000
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "time": "$before"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractMinute() {
        assertQueryResult(
                "select extract(minute from before) from Item",
                ITEM.before.atZone(UTC).getMinute(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$minute": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractHour() {
        assertQueryResult(
                "select extract(hour from before) from Item",
                ITEM.before.atZone(UTC).getHour(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$hour": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"day", "day of month"})
    void testExtractDay(String unit) {
        assertQueryResult(
                "select extract(%s from before) from Item".formatted(unit),
                ITEM.before.atZone(UTC).getDayOfMonth(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfMonth": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractMonth() {
        assertQueryResult(
                "select extract(month from before) from Item",
                ITEM.before.atZone(UTC).getMonthValue(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$month": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractYear() {
        assertQueryResult(
                "select extract(year from before) from Item",
                ITEM.before.atZone(UTC).getYear(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$year": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractQuarter() {
        assertQueryResult(
                "select extract(quarter from before) from Item",
                ITEM.before.atZone(UTC).get(IsoFields.QUARTER_OF_YEAR),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$toInt": {
                            "$ceil": {
                              "$divide": [
                                {
                                  "$month": "$before"
                                },
                                {
                                  "$literal": 3
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractWeekOfYear() {
        assertQueryResult(
                "select extract(week of year from before) from Item",
                ITEM.before.atZone(UTC).get(ChronoField.ALIGNED_WEEK_OF_YEAR),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$isoWeek": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractWeek() {
        // Java uses 1-based weeks while Mongo uses 0-based.
        assertQueryResult(
                "select extract(week from before) from Item",
                ITEM.before.atZone(UTC).get(ChronoField.ALIGNED_WEEK_OF_YEAR) - 1,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$week": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractWeekOfMonth() {
        assertQueryResult(
                "select extract(week of month from after) from Item",
                ITEM.before.atZone(UTC).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$subtract": [
                                {
                                  "$week": "$$time"
                                },
                                {
                                  "$week": {
                                    "$dateTrunc": {
                                      "date": "$$time",
                                      "unit": {
                                        "$literal": "month"
                                      }
                                    }
                                  }
                                }
                              ]
                            },
                            "vars": {
                              "time": "$after"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractDayOfWeek() {
        assertQueryResult(
                "select extract(day of week from before) from Item",
                javaDayOfWeekToMongo(ITEM.before.atZone(UTC).get(ChronoField.DAY_OF_WEEK)),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfWeek": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractDayOfYear() {
        assertQueryResult(
                "select extract(day of year from before) from Item",
                ITEM.before.atZone(UTC).getDayOfYear(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfYear": "$before"
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractEpoch() {
        assertQueryResult(
                "select extract(epoch from before) from Item",
                ITEM.before.getEpochSecond(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$toLong": {
                            "$divide": [
                              {
                                "$toLong": "$before"
                              },
                              {
                                "$literal": 1000
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testExtractNanosecond() {
        assertQueryResult(
                "select extract(nanosecond from before) from Item",
                (long) (ITEM.before.atZone(UTC).getNano()
                        + ITEM.before.atZone(UTC).getSecond() * 1e9),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$toLong": {
                                "$add": [
                                  {
                                    "$multiply": [
                                      {
                                        "$millisecond": "$$time"
                                      },
                                      {
                                        "$literal": 1000000
                                      }
                                    ]
                                  },
                                  {
                                    "$multiply": [
                                      {
                                        "$second": "$$time"
                                      },
                                      {
                                        "$literal": 1000000000
                                      }
                                    ]
                                  }
                                ]
                              }
                            },
                            "vars": {
                              "time": "$before"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        @Test
        void testExtractTime() {
            assertSelectQueryFailure(
                    "select extract(time from before) from Item",
                    LocalTime.class,
                    FeatureNotSupportedException.class,
                    "Time unit time not supported");
        }

        @Test
        void testExtractOffset() {
            assertSelectQueryFailure(
                    "select extract(offset from before) from Item",
                    ZoneOffset.class,
                    FeatureNotSupportedException.class,
                    "Time unit offset not supported");
        }

        @Test
        void testExtractTimeZoneHour() {
            assertSelectQueryFailure(
                    "select extract(timezone_hour from before) from Item",
                    Integer.class,
                    FeatureNotSupportedException.class,
                    "Time unit timezone_hour not supported");
        }

        @Test
        void testExtractTimeZoneMinute() {
            assertSelectQueryFailure(
                    "select extract(timezone_minute from before) from Item",
                    Integer.class,
                    FeatureNotSupportedException.class,
                    "Time unit timezone_minute not supported");
        }
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        Instant before;
        Instant after;

        Item() {}

        Item(int id, Instant before, Instant after) {
            this.id = id;
            this.before = before;
            this.after = after;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return id == item.id && Objects.equals(before, item.before) && Objects.equals(after, item.after);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, before, after);
        }

        @Override
        public String toString() {
            return "Item{" + "id=" + id + ", s='" + before + '\'' + ", u='" + after + '\'' + '}';
        }
    }
}

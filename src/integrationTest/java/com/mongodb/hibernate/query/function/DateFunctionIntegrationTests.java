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
import java.util.Locale;
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
    @CsvSource({
        "yyyy-MM-dd HH:mm:ss,%Y-%m-%d %H:%M:%S",
        "yyyy-MM-dd 'HH%H' HH:mm:ss,%Y-%m-%d HH%%H %H:%M:%S",
        "DDD,%j",
        "HH,%H",
        "MM,%m",
        "MMM,%b",
        "MMMM,%B",
        "SSS,%L",
        "YYYY,%G",
        "Z,%z",
        "ZZ,%z",
        "ZZZ,%z",
        "dd,%d",
        "mm,%M",
        "ss,%S",
        "xx,%z",
        "yyyy,%Y",
    })
    void testFormat(String hqlFormat, String mqlFormat) {
        // Mongo uses fixed date names that match the US locale in Java. See
        // `mongo/db/query/datetime/date_time_support.cpp`
        assertQueryResult(
                "select format(before as '%s') from Item".formatted(hqlFormat.replace("'", "''")),
                ITEM.before.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(hqlFormat, Locale.US)),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dateToString": {
                            "date": "$before",
                            "format": { "$literal": "%2$s" },
                            "timezone": { "$literal": "%1$s" }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId(), mqlFormat));
    }

    @Test
    void testFormatFunction() {
        assertQueryResult(
                "select format(before, '%Y-%m-%d %H:%M:%S') from Item",
                ITEM.before
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dateToString": {
                            "date": "$before",
                            "format": "%%Y-%%m-%%d %%H:%%M:%%S",
                            "timezone": { "$literal": "%1$s" }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractSecond() {
        assertQueryResult(
                "select extract(second from before) from Item",
                (float) (ITEM.before.atZone(ZoneId.systemDefault()).getSecond()
                        + ITEM.before.atZone(ZoneId.systemDefault()).getNano() / 1e9),
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
                ITEM.before.atZone(ZoneId.systemDefault()).getMinute(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$minute": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractHour() {
        assertQueryResult(
                "select extract(hour from before) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).getHour(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$hour": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"day", "day of month"})
    void testExtractDay(String unit) {
        assertQueryResult(
                "select extract(%s from before) from Item".formatted(unit),
                ITEM.before.atZone(ZoneId.systemDefault()).getDayOfMonth(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfMonth": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractMonth() {
        assertQueryResult(
                "select extract(month from before) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).getMonthValue(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$month": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractYear() {
        assertQueryResult(
                "select extract(year from before) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).getYear(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$year": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractQuarter() {
        assertQueryResult(
                "select extract(quarter from before) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).get(IsoFields.QUARTER_OF_YEAR),
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
                                  "$month": {
                                    "date": "$before",
                                    "timezone": {
                                      "$literal": "%1$s"
                                    }
                                  }
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
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractWeekOfYear() {
        assertQueryResult(
                "select extract(week of year from before) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).get(ChronoField.ALIGNED_WEEK_OF_YEAR),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$isoWeek": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractWeek() {
        // Java uses 1-based weeks while Mongo uses 0-based.
        assertQueryResult(
                "select extract(week from before) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).get(ChronoField.ALIGNED_WEEK_OF_YEAR) - 1,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$week": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractWeekOfMonth() {
        assertQueryResult(
                "select extract(week of month from after) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
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
                                  "$week": {
                                    "date": "$$time",
                                    "timezone": {
                                      "$literal": "%1$s"
                                    }
                                  }
                                },
                                {
                                  "$week": {
                                    "$dateTrunc": {
                                      "date": "$$time",
                                      "timezone": {
                                        "$literal": "%1$s"
                                      },
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
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractDayOfWeek() {
        assertQueryResult(
                "select extract(day of week from before) from Item",
                javaDayOfWeekToMongo(ITEM.before.atZone(ZoneId.systemDefault()).get(ChronoField.DAY_OF_WEEK)),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfWeek": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    @Test
    void testExtractDayOfYear() {
        assertQueryResult(
                "select extract(day of year from before) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).getDayOfYear(),
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfYear": {
                            "date": "$before",
                            "timezone": {
                              "$literal": "%1$s"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
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
                (long) (ITEM.before.atZone(ZoneId.systemDefault()).getNano()
                        + ITEM.before.atZone(ZoneId.systemDefault()).getSecond() * 1e9),
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
                                        "$millisecond": {
                                          "date": "$$time",
                                          "timezone": {
                                            "$literal": "%1$s"
                                          }
                                        }
                                      },
                                      {
                                        "$literal": 1000000
                                      }
                                    ]
                                  },
                                  {
                                    "$multiply": [
                                      {
                                        "$second": {
                                          "date": "$$time",
                                          "timezone": {
                                            "$literal": "%1$s"
                                          }
                                        }
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
                """
                        .formatted(ZoneId.systemDefault().getId()));
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

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "D", "DD", "EEE", "EEEE", "G", "GG", "H", "M", "S", "SS", "SSSS", "SSSSS", "SSSSSS", "W", "Y", "YY",
                    "YYY", "a", "d", "e", "ee", "h", "hh", "m", "s", "w", "ww", "x", "xxx", "y", "yy", "yyy",
                    "z", "zz", "zzz"
                })
        void testFormatUnsupported(String format) {
            assertSelectQueryFailure(
                    "select format(before as '%s') from Item".formatted(format),
                    String.class,
                    FeatureNotSupportedException.class,
                    "Unsupported date format: " + format);
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

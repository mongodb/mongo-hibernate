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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
    private static final Item ITEM = new Item(
            1,
            Instant.ofEpochMilli(123456789),
            Instant.ofEpochMilli(654321987),
            ZonedDateTime.of(2019, 1, 1, 14, 12, 10, 0, ZoneId.systemDefault()).toInstant(),
            ZonedDateTime.of(2019, 1, 5, 14, 12, 10, 0, ZoneId.systemDefault()).toInstant(),
            ZonedDateTime.of(2019, 5, 31, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant(),
            ZonedDateTime.of(2019, 5, 30, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant(),
            ZonedDateTime.of(2019, 5, 27, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant(),
            ZonedDateTime.of(2019, 5, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant());

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
        "DDD:,%j:",
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
        "ww,%V",
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
    void testFormatConcatenateCode() {
        assertSelectQueryFailure(
                "select format(before as 'ZZZZZZZ') from Item",
                String.class,
                FeatureNotSupportedException.class,
                "Format code ZZZZZZZ is ambiguous.");
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
                                  "$second": {
                                    "date": "$$time",
                                    "timezone": {
                                      "$literal": "%1$s"
                                    }
                                  }
                                },
                                {
                                  "$divide": [
                                    {
                                      "$millisecond": {
                                        "date": "$$time",
                                        "timezone": {
                                          "$literal": "%1$s"
                                        }
                                      }
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
                """
                        .formatted(ZoneId.systemDefault().getId()));
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
                sundayBasedWeek(ITEM.before.atZone(ZoneId.systemDefault()).getDayOfYear()),
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
                                  "$toInt": {
                                    "$ceil": {
                                      "$divide": [
                                        {
                                          "$subtract": [
                                            {
                                              "$dayOfYear": {
                                                "date": "$$time",
                                                "timezone": { "$literal": "%1$s" }
                                              }
                                            },
                                            {
                                              "$dayOfWeek": {
                                                "date": "$$time",
                                                "timezone": { "$literal": "%1$s" }
                                              }
                                            }
                                          ]
                                        },
                                        { "$literal": 7 }
                                      ]
                                    }
                                  }
                                },
                                { "$literal": 1 }
                              ]
                            },
                            "vars": { "time": "$before" }
                          }
                        }
                      }
                    }
                  ]
                }
                """
                        .formatted(ZoneId.systemDefault().getId()));
    }

    /**
     * Hibernate defines both Sunday-based week units as {@code ceiling((dayOfPeriod - dayOfWeek)/7.0 + 1)}, with Sunday
     * as day one.
     */
    private static int sundayBasedWeek(int dayOfPeriod) {
        var isoDayOfWeek = ITEM.before.atZone(ZoneId.systemDefault()).get(ChronoField.DAY_OF_WEEK);
        return (int) Math.ceil((dayOfPeriod - ((isoDayOfWeek % 7) + 1)) / 7.0 + 1);
    }

    @Test
    void testExtractWeek() {
        assertQueryResult(
                "select extract(week from before) from Item",
                ITEM.before.atZone(ZoneId.systemDefault()).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
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
    void testExtractWeekOfMonth() {
        assertQueryResult(
                "select extract(week of month from after) from Item",
                ITEM.after.atZone(ZoneId.systemDefault()).get(ChronoField.ALIGNED_WEEK_OF_MONTH),
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
                                  "$toInt": {
                                    "$ceil": {
                                      "$divide": [
                                        {
                                          "$subtract": [
                                            {
                                              "$dayOfMonth": {
                                                "date": "$$time",
                                                "timezone": {
                                                  "$literal": "%1$s"
                                                }
                                              }
                                            },
                                            {
                                              "$dayOfWeek": {
                                                "date": "$$time",
                                                "timezone": {
                                                  "$literal": "%1$s"
                                                }
                                              }
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": 7
                                        }
                                      ]
                                    }
                                  }
                                },
                                {
                                  "$literal": 1
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

    @Test
    void testExtractWeekOfYear1FromHibernateTestSuite() {
        assertQueryResult(
                "select extract(week of year from newYears) from Item",
                1,
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
                                  "$toInt": {
                                    "$ceil": {
                                      "$divide": [
                                        {
                                          "$subtract": [
                                            {
                                              "$dayOfYear": {
                                                "date": "$$time",
                                                "timezone": {
                                                  "$literal": "%1$s"
                                                }
                                              }
                                            },
                                            {
                                              "$dayOfWeek": {
                                                "date": "$$time",
                                                "timezone": {
                                                  "$literal": "%1$s"
                                                }
                                              }
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": 7
                                        }
                                      ]
                                    }
                                  }
                                },
                                {
                                  "$literal": 1
                                }
                              ]
                            },
                            "vars": {
                              "time": "$newYears"
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
    void testExtractWeekOfYear2FromHibernateTestSuite() {
        assertQueryResult(
                "select extract(week of year from earlyJanuary) from Item",
                1,
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
                                  "$toInt": {
                                    "$ceil": {
                                      "$divide": [
                                        {
                                          "$subtract": [
                                            {
                                              "$dayOfYear": {
                                                "date": "$$time",
                                                "timezone": {
                                                  "$literal": "%1$s"
                                                }
                                              }
                                            },
                                            {
                                              "$dayOfWeek": {
                                                "date": "$$time",
                                                "timezone": {
                                                  "$literal": "%1$s"
                                                }
                                              }
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": 7
                                        }
                                      ]
                                    }
                                  }
                                },
                                {
                                  "$literal": 1
                                }
                              ]
                            },
                            "vars": {
                              "time": "$earlyJanuary"
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
    void testExtractWeekOfMonthFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(week of month from startOfMay) from Item",
                1,
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
                                  "$toInt": {
                                    "$ceil": {
                                      "$divide": [
                                        {
                                          "$subtract": [
                                            {
                                              "$dayOfMonth": {
                                                "date": "$$time",
                                                "timezone": {
                                                  "$literal": "%1$s"
                                                }
                                              }
                                            },
                                            {
                                              "$dayOfWeek": {
                                                "date": "$$time",
                                                "timezone": {
                                                  "$literal": "%1$s"
                                                }
                                              }
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": 7
                                        }
                                      ]
                                    }
                                  }
                                },
                                {
                                  "$literal": 1
                                }
                              ]
                            },
                            "vars": {
                              "time": "$startOfMay"
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
    void testExtractWeekFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(week from lateMay) from Item",
                22,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$isoWeek": {
                            "date": "$lateMay",
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
    void testExtractDayOfYearFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(day of year from almostEndOfMay) from Item",
                150,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfYear": {
                            "date": "$almostEndOfMay",
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
    void testExtractDayOfMonthFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(day of month from lateMay) from Item",
                27,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfMonth": {
                            "date": "$lateMay",
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
    void testExtractDayFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(day from endOfMay) from Item",
                31,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfMonth": {
                            "date": "$endOfMay",
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
    void testExtractMonthFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(month from endOfMay) from Item",
                5,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$month": {
                            "date": "$endOfMay",
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
    void testExtractYearFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(year from endOfMay) from Item",
                2019,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$year": {
                            "date": "$endOfMay",
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
    void testExtractQuarterFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(quarter from endOfMay) from Item",
                2,
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
                                    "date": "$endOfMay",
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
    void testExtractDayOfWeekFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(day of week from lateMay) from Item",
                2,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfWeek": {
                            "date": "$lateMay",
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
    void testExtractDayOfWeek2FromHibernateTestSuite() {
        assertQueryResult(
                "select extract(day of week from endOfMay) from Item",
                6,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$dayOfWeek": {
                            "date": "$endOfMay",
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
    void testExtractSecondFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(second from newYears) from Item",
                10f,
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
                                  "$second": {
                                    "date": "$$time",
                                    "timezone": {
                                      "$literal": "%1$s"
                                    }
                                  }
                                },
                                {
                                  "$divide": [
                                    {
                                      "$millisecond": {
                                        "date": "$$time",
                                        "timezone": {
                                          "$literal": "%1$s"
                                        }
                                      }
                                    },
                                    {
                                      "$literal": 1000
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "time": "$newYears"
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
    void testExtractMinuteFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(minute from newYears) from Item",
                12,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$minute": {
                            "date": "$newYears",
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
    void testExtractHourFromHibernateTestSuite() {
        assertQueryResult(
                "select extract(hour from newYears) from Item",
                14,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$hour": {
                            "date": "$newYears",
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

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        @Test
        void testExtractDate() {
            assertSelectQueryFailure(
                    "select extract(date from before) from Item",
                    LocalDate.class,
                    FeatureNotSupportedException.class,
                    "Time unit date not supported");
        }

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
                    "YYY", "a", "d", "e", "ee", "h", "hh", "m", "s", "w", "x", "xxx", "y", "yy", "yyy", "z", "zz", "zzz"
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
        Instant newYears;
        Instant earlyJanuary;
        Instant endOfMay;
        Instant almostEndOfMay;
        Instant lateMay;
        Instant startOfMay;

        Item() {}

        Item(
                int id,
                Instant before,
                Instant after,
                Instant newYears,
                Instant earlyJanuary,
                Instant endOfMay,
                Instant almostEndOfMay,
                Instant lateMay,
                Instant start_of_may) {
            this.id = id;
            this.before = before;
            this.after = after;
            this.newYears = newYears;
            this.earlyJanuary = earlyJanuary;
            this.endOfMay = endOfMay;
            this.almostEndOfMay = almostEndOfMay;
            this.lateMay = lateMay;

            startOfMay = start_of_may;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return id == item.id
                    && Objects.equals(before, item.before)
                    && Objects.equals(after, item.after)
                    && Objects.equals(newYears, item.newYears)
                    && Objects.equals(earlyJanuary, item.earlyJanuary)
                    && Objects.equals(endOfMay, item.endOfMay)
                    && Objects.equals(almostEndOfMay, item.almostEndOfMay)
                    && Objects.equals(lateMay, item.lateMay)
                    && Objects.equals(startOfMay, item.startOfMay);
        }

        @Override
        public String toString() {
            return "Item{" + "id="
                    + id + ", before="
                    + before + ", after="
                    + after + ", newYears="
                    + newYears + ", earlyJanuary="
                    + earlyJanuary + ", endOfMay="
                    + endOfMay + ", almostEndOfMay="
                    + almostEndOfMay + ", lateMay="
                    + lateMay + ", startOfMay="
                    + startOfMay + '}';
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    id, before, after, newYears, earlyJanuary, endOfMay, almostEndOfMay, lateMay, startOfMay);
        }
    }
}

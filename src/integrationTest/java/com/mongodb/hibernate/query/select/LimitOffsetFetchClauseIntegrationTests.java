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

package com.mongodb.hibernate.query.select;

import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static java.lang.String.format;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hibernate.cfg.JdbcSettings.DIALECT;
import static org.hibernate.cfg.QuerySettings.QUERY_PLAN_CACHE_ENABLED;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import com.mongodb.hibernate.dialect.MongoDialect;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.MongoConstants;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.sqm.FetchClauseType;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DomainModel(annotatedClasses = Book.class)
class LimitOffsetFetchClauseIntegrationTests extends AbstractSelectionQueryIntegrationTests {

    private static final List<Book> testingBooks = List.of(
            new Book(0, "Nostromo", 1904, true),
            new Book(1, "The Age of Innocence", 1920, false),
            new Book(2, "Remembrance of Things Past", 1913, true),
            new Book(3, "The Magic Mountain", 1924, false),
            new Book(4, "A Passage to India", 1924, true),
            new Book(5, "Ulysses", 1922, false),
            new Book(6, "Mrs. Dalloway", 1925, false),
            new Book(7, "The Trial", 1925, true),
            new Book(8, "Sons and Lovers", 1913, false),
            new Book(9, "The Sound and the Fury", 1929, false));

    private static List<Book> getBooksByIds(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> testingBooks.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .toList();
    }

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> testingBooks.forEach(session::persist));
        getTestCommandListener().clear();
    }

    @Nested
    class WithoutQueryOptionsLimit {

        @Test
        void testHqlLimitClauseOnly() {
            assertSelectionQuery(
                    "from Book order by id LIMIT :limit",
                    Book.class,
                    q -> q.setParameter("limit", 5),
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "_id": 1
                          }
                        },
                        {
                          "$limit": %d
                        },
                        {
                          "$project": {
                            "_id": true,
                            "discount": true,
                            "isbn13": true,
                            "outOfStock": true,
                            "price": true,
                            "publishYear": true,
                            "title": true
                          }
                        }
                      ]
                    }
                    """
                            .formatted(5),
                    getBooksByIds(0, 1, 2, 3, 4));
        }

        @Test
        void testHqlOffsetClauseOnly() {
            assertSelectionQuery(
                    "from Book order by id OFFSET :offset",
                    Book.class,
                    q -> q.setParameter("offset", 7),
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "_id": 1
                          }
                        },
                        {
                          "$skip": %d
                        },
                        {
                          "$project": {
                            "_id": true,
                            "discount": true,
                            "isbn13": true,
                            "outOfStock": true,
                            "price": true,
                            "publishYear": true,
                            "title": true
                          }
                        }
                      ]
                    }
                    """
                            .formatted(7),
                    getBooksByIds(7, 8, 9));
        }

        @Test
        void testHqlLimitAndOffsetClauses() {
            assertSelectionQuery(
                    "from Book order by id LIMIT :limit OFFSET :offset",
                    Book.class,
                    q -> q.setParameter("offset", 3).setParameter("limit", 2),
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "_id": 1
                          }
                        },
                        {
                          "$skip": %d
                        },
                        {
                          "$limit": %d
                        },
                        {
                          "$project": {
                            "_id": true,
                            "discount": true,
                            "isbn13": true,
                            "outOfStock": true,
                            "price": true,
                            "publishYear": true,
                            "title": true
                          }
                        }
                      ]
                    }
                    """
                            .formatted(3, 2),
                    getBooksByIds(3, 4));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "FETCH FIRST :limit ROWS ONLY",
                    "FETCH NEXT :limit ROWS ONLY",
                })
        void testHqlFetchClauseOnly(final String fetchClause) {
            assertSelectionQuery(
                    "from Book order by id " + fetchClause,
                    Book.class,
                    q -> q.setParameter("limit", 5),
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "_id": 1
                          }
                        },
                        {
                          "$limit": %d
                        },
                        {
                          "$project": {
                            "_id": true,
                            "discount": true,
                            "isbn13": true,
                            "outOfStock": true,
                            "price": true,
                            "publishYear": true,
                            "title": true
                          }
                        }
                      ]
                    }
                    """
                            .formatted(5),
                    getBooksByIds(0, 1, 2, 3, 4));
        }
    }

    @Nested
    class WithQueryOptionsLimit {

        @Nested
        class WithoutHqlClauses {
            @Test
            void testQueryOptionsSetFirstResultOnly() {
                assertSelectionQuery(
                        "from Book order by id",
                        Book.class,
                        q -> q.setFirstResult(6),
                        """
                        {
                          "aggregate": "books",
                          "pipeline": [
                            {
                              "$sort": {
                                "_id": 1
                              }
                            },
                            {
                              "$skip": %d
                            },
                            {
                              "$project": {
                                "_id": true,
                                "discount": true,
                                "isbn13": true,
                                "outOfStock": true,
                                "price": true,
                                "publishYear": true,
                                "title": true
                              }
                            }
                          ]
                        }
                        """
                                .formatted(6),
                        getBooksByIds(6, 7, 8, 9));
            }

            @Test
            void testQueryOptionsSetMaxResultOnly() {
                assertSelectionQuery(
                        "from Book order by id",
                        Book.class,
                        q -> q.setMaxResults(3),
                        """
                        {
                          "aggregate": "books",
                          "pipeline": [
                            {
                              "$sort": {
                                "_id": 1
                              }
                            },
                            {
                              "$limit": %d
                            },
                            {
                              "$project": {
                                "_id": true,
                                "discount": true,
                                "isbn13": true,
                                "outOfStock": true,
                                "price": true,
                                "publishYear": true,
                                "title": true
                              }
                            }
                          ]
                        }
                        """
                                .formatted(3),
                        getBooksByIds(0, 1, 2));
            }

            @Test
            void testQueryOptionsSetFirstResultAndMaxResults() {
                assertSelectionQuery(
                        "from Book order by id",
                        Book.class,
                        q -> q.setFirstResult(2).setMaxResults(3),
                        """
                        {
                          "aggregate": "books",
                          "pipeline": [
                            {
                              "$sort": {
                                "_id": 1
                              }
                            },
                            {
                              "$skip": %d
                            },
                            {
                              "$limit": %d
                            },
                            {
                              "$project": {
                                "_id": true,
                                "discount": true,
                                "isbn13": true,
                                "outOfStock": true,
                                "price": true,
                                "publishYear": true,
                                "title": true
                              }
                            }
                          ]
                        }
                        """
                                .formatted(2, 3),
                        getBooksByIds(2, 3, 4));
            }
        }

        @Nested
        class WithHqlClauses {

            private static final String expectedMqlTemplate =
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "_id": 1
                          }
                        },
                        %s,
                        {
                          "$project": {
                            "_id": true,
                            "discount": true,
                            "isbn13": true,
                            "outOfStock": true,
                            "price": true,
                            "publishYear": true,
                            "title": true
                          }
                        }
                      ]
                    }
                    """;

            @Test
            void testFirstResultConflictingOnly() {
                var firstResult = 5;
                var expectedBooks = getBooksByIds(5, 6, 7, 8, 9);
                assertSelectionQuery(
                        "from Book order by id LIMIT :limit OFFSET :offset",
                        Book.class,
                        q ->
                                // hql clauses will be ignored totally
                                q.setParameter("limit", 10)
                                        .setParameter("offset", 0)
                                        .setFirstResult(firstResult),
                        expectedMqlTemplate.formatted("{\"$skip\": " + firstResult + "}"),
                        expectedBooks);
            }

            @Test
            void testMaxResultsConflictingOnly() {
                var maxResults = 3;
                var expectedBooks = getBooksByIds(0, 1, 2);
                assertSelectionQuery(
                        "from Book order by id LIMIT :limit OFFSET :offset",
                        Book.class,
                        q ->
                                // hql clauses will be ignored totally
                                q.setParameter("limit", 10)
                                        .setParameter("offset", 0)
                                        .setMaxResults(maxResults),
                        expectedMqlTemplate.formatted("{\"$limit\": " + maxResults + "}"),
                        expectedBooks);
            }

            @Test
            void testBothFirstResultAndMaxResultsConflicting() {
                var firstResult = 5;
                var maxResults = 3;
                var expectedBooks = getBooksByIds(5, 6, 7);
                assertSelectionQuery(
                        "from Book order by id LIMIT :limit OFFSET :offset",
                        Book.class,
                        q ->
                                // hql clauses will be ignored totally
                                q.setParameter("limit", 10)
                                        .setParameter("offset", 0)
                                        .setFirstResult(firstResult)
                                        .setMaxResults(maxResults),
                        expectedMqlTemplate.formatted(
                                "{\"$skip\": " + firstResult + "}," + "{\"$limit\": " + maxResults + "}"),
                        expectedBooks);
            }
        }
    }

    @Nested
    class FeatureNotSupportedTests {

        @ParameterizedTest
        @EnumSource(value = FetchClauseType.class, mode = EXCLUDE, names = "ROWS_ONLY")
        void testUnsupportedFetchClauseType(FetchClauseType fetchClauseType) {
            var hqlSuffix =
                    switch (fetchClauseType) {
                        case ROWS_ONLY -> fail("ROWS_ONLY should have been excluded from the test");
                        case ROWS_WITH_TIES -> "FETCH FIRST :limit ROWS WITH TIES";
                        case PERCENT_ONLY -> "FETCH FIRST :limit PERCENT ROWS ONLY";
                        case PERCENT_WITH_TIES -> "FETCH FIRST :limit PERCENT ROWS WITH TIES";
                    };
            var hql = "from Book order by id " + hqlSuffix;
            assertSelectQueryFailure(
                    hql,
                    Book.class,
                    q -> q.setParameter("limit", 10),
                    FeatureNotSupportedException.class,
                    "%s does not support '%s' fetch clause type",
                    MongoConstants.MONGO_DBMS_NAME,
                    fetchClauseType);
        }
    }

    @Nested
    @DomainModel(annotatedClasses = Book.class)
    @ServiceRegistry(
            settings = {
                @Setting(name = QUERY_PLAN_CACHE_ENABLED, value = "true"),
                @Setting(
                        name = DIALECT,
                        value =
                                "com.mongodb.hibernate.query.select.LimitOffsetFetchClauseIntegrationTests$TranslatingCacheTestingDialect"),
            })
    class QueryPlanCacheTests extends AbstractSelectionQueryIntegrationTests {

        private static final String HQL = "from Book order by id";
        private static final String expectedMqlTemplate =
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    %s
                    %s
                    {
                      "$project": {
                        "_id": true,
                        "discount": true,
                        "isbn13": true,
                        "outOfStock": true,
                        "price": true,
                        "publishYear": true,
                        "title": true
                      }
                    }
                  ]
                }
                """;

        private TranslatingCacheTestingDialect translatingCacheTestingDialect;

        @BeforeEach
        void beforeEach() {
            translatingCacheTestingDialect = (TranslatingCacheTestingDialect) getSessionFactoryScope()
                    .getSessionFactory()
                    .getJdbcServices()
                    .getDialect();
            getTestCommandListener().clear();
        }

        @ParameterizedTest
        @CsvSource({"true,false", "false,true", "true,true"})
        void testQueryOptionsLimitCached(boolean isFirstResultSet, boolean isMaxResultsSet) {
            getSessionFactoryScope().inTransaction(session -> {
                setQueryOptionsAndQuery(
                        session,
                        isFirstResultSet ? 5 : null,
                        isMaxResultsSet ? 10 : null,
                        format(
                                expectedMqlTemplate,
                                (isFirstResultSet ? "{\"$skip\": 5}," : ""),
                                (isMaxResultsSet ? "{\"$limit\": 10}," : "")));
                var initialSelectTranslatingCount = translatingCacheTestingDialect.getSelectTranslatingCounter();

                assertThat(initialSelectTranslatingCount).isPositive();

                setQueryOptionsAndQuery(
                        session,
                        isFirstResultSet ? 3 : null,
                        isMaxResultsSet ? 6 : null,
                        format(
                                expectedMqlTemplate,
                                (isFirstResultSet ? "{\"$skip\": 3}," : ""),
                                (isMaxResultsSet ? "{\"$limit\": 6}," : "")));
                assertThat(translatingCacheTestingDialect.getSelectTranslatingCounter())
                        .isEqualTo(initialSelectTranslatingCount);
            });
        }

        @Test
        void testCacheInvalidatedDueToQueryOptionsAdded() {
            getSessionFactoryScope().inTransaction(session -> {
                setQueryOptionsAndQuery(session, null, null, format(expectedMqlTemplate, "", ""));
                var initialSelectTranslatingCount = translatingCacheTestingDialect.getSelectTranslatingCounter();

                assertThat(initialSelectTranslatingCount).isPositive();

                setQueryOptionsAndQuery(session, 1, null, format(expectedMqlTemplate, "{\"$skip\": 1},", ""));
                assertThat(translatingCacheTestingDialect.getSelectTranslatingCounter())
                        .isEqualTo(initialSelectTranslatingCount + 1);

                setQueryOptionsAndQuery(
                        session, 1, 5, format(expectedMqlTemplate, "{\"$skip\": 1},", "{\"$limit\": 5},"));
                assertThat(translatingCacheTestingDialect.getSelectTranslatingCounter())
                        .isEqualTo(initialSelectTranslatingCount + 2);
            });
        }

        @Test
        void testCacheInvalidatedDueToQueryOptionsRemoved() {
            getSessionFactoryScope().inTransaction(session -> {
                setQueryOptionsAndQuery(session, 10, null, format(expectedMqlTemplate, "{\"$skip\": 10},", ""));

                var initialSelectTranslatingCount = translatingCacheTestingDialect.getSelectTranslatingCounter();

                assertThat(initialSelectTranslatingCount).isPositive();

                setQueryOptionsAndQuery(session, null, null, format(expectedMqlTemplate, "", ""));

                assertThat(translatingCacheTestingDialect.getSelectTranslatingCounter())
                        .isEqualTo(initialSelectTranslatingCount + 1);
            });
        }

        @Test
        void testCacheInvalidatedDueToQueryOptionsChanged() {
            getSessionFactoryScope().inTransaction(session -> {
                setQueryOptionsAndQuery(session, 10, null, format(expectedMqlTemplate, "{\"$skip\": 10},", ""));

                var initialSelectTranslatingCount = translatingCacheTestingDialect.getSelectTranslatingCounter();

                assertThat(initialSelectTranslatingCount).isPositive();

                setQueryOptionsAndQuery(session, null, 20, format(expectedMqlTemplate, "", "{\"$limit\": 20},"));

                assertThat(translatingCacheTestingDialect.getSelectTranslatingCounter())
                        .isEqualTo(initialSelectTranslatingCount + 1);
            });
        }

        private void setQueryOptionsAndQuery(
                Session session, Integer firstResult, Integer maxResults, String expectedMql) {
            var query = session.createSelectionQuery(HQL, Book.class);
            if (firstResult != null) {
                query.setFirstResult(firstResult);
            }
            if (maxResults != null) {
                query.setMaxResults(maxResults);
            }
            getTestCommandListener().clear();
            query.getResultList();
            if (expectedMql != null) {
                var expectedCommand = BsonDocument.parse(expectedMql);
                assertActualCommand(expectedCommand);
            }
        }
    }

    /**
     * A dialect that counts how many times the select translator is created.
     *
     * <p>Note that {@link QueryStatistics#getPlanCacheHitCount()} is not used, because it counts the number of times
     * the query plan cache is hit, not whether {@link SqlAstTranslator} is reused afterwards (e.g., incompatible
     * {@link org.hibernate.query.spi.QueryOptions QueryOptions}s will end up with new translator bing created).
     */
    public static final class TranslatingCacheTestingDialect extends Dialect {
        private final AtomicInteger selectTranslatingCounter = new AtomicInteger();
        private final Dialect delegate;

        public TranslatingCacheTestingDialect(DialectResolutionInfo info) {
            super(info);
            delegate = new MongoDialect(info);
        }

        @Override
        public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
            return new SqlAstTranslatorFactory() {
                @Override
                public SqlAstTranslator<JdbcOperationQuerySelect> buildSelectTranslator(
                        SessionFactoryImplementor sessionFactory, SelectStatement statement) {
                    selectTranslatingCounter.incrementAndGet();
                    return delegate.getSqlAstTranslatorFactory().buildSelectTranslator(sessionFactory, statement);
                }

                @Override
                public SqlAstTranslator<? extends JdbcOperationQueryMutation> buildMutationTranslator(
                        SessionFactoryImplementor sessionFactory, MutationStatement statement) {
                    throw new IllegalStateException("mutation translator not expected");
                }

                @Override
                public <O extends JdbcMutationOperation> SqlAstTranslator<O> buildModelMutationTranslator(
                        TableMutation<O> mutation, SessionFactoryImplementor sessionFactory) {
                    return delegate.getSqlAstTranslatorFactory().buildModelMutationTranslator(mutation, sessionFactory);
                }
            };
        }

        public int getSelectTranslatingCounter() {
            return selectTranslatingCounter.get();
        }
    }
}

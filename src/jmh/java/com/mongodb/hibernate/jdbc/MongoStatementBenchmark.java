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

package com.mongodb.hibernate.jdbc;

import org.bson.BsonDocument;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class MongoStatementBenchmark {

    private static final BsonDocument EXAMPLE_PROJECT_STAGE = BsonDocument.parse(
            """
            { f1: 1, f2: 1, f3: 1, f4: 1, f5: 1, f6: 1, f7: 1, f8: 1, f9: 1, f10: 1, f11: 1, f12: 1, _id: 0}\
            """);

    @Benchmark
    public void getProjectStageFieldNames(Blackhole bh) {
        var fieldNames = MongoStatement.getFieldNamesFromProjectStage(EXAMPLE_PROJECT_STAGE);
        bh.consume(fieldNames);
    }
}

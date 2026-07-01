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

package com.mongodb.hibernate.naming;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClients;
import java.util.Objects;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;

final class NamingTestSupport {

    private NamingTestSupport() {}

    // Reads the raw stored document straight from MongoDB (bypassing Hibernate) so the test inspects the
    // actual persisted field keys rather than the entity's Java property names.
    static BsonDocument readStoredBook(String connectionString, ObjectId id) {
        try (var client = MongoClients.create(connectionString)) {
            var databaseName = Objects.requireNonNull(
                    new ConnectionString(connectionString).getDatabase(),
                    "connection string must include a database name");
            var document = client.getDatabase(databaseName)
                    .getCollection("namingbook", BsonDocument.class)
                    .find(new BsonDocument("_id", new BsonObjectId(id)))
                    .first();
            return Objects.requireNonNull(document, () -> "no stored document for id " + id);
        }
    }
}

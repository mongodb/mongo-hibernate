/*
 * Copyright 2026-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.jdbc;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;

class CreateIndexesCommand implements Command<Void> {
    private final String collectionName;
    private final List<IndexModel> indexes;

    CreateIndexesCommand(BsonDocument commandDocument) throws SQLFeatureNotSupportedException {
        CommandDescription.CREATE_INDEXES.checkCommandFields(commandDocument);
        this.collectionName = commandDocument.getString("createIndexes").getValue();
        this.indexes = new ArrayList<>();
        for (var indexValue : commandDocument.getArray("indexes")) {
            var indexDocument = indexValue.asDocument();
            CommandDescription.CREATE_INDEXES.checkStatementFields(indexDocument);
            this.indexes.add(new IndexModel(
                    indexDocument.getDocument("key"),
                    new IndexOptions()
                            .name(indexDocument.getString("name").getValue())
                            .unique(indexDocument.getBoolean("unique").getValue())));
        }
    }

    @Override
    public Void execute(MongoDatabase database) {
        database.getCollection(collectionName).createIndexes(indexes);
        return null;
    }
}

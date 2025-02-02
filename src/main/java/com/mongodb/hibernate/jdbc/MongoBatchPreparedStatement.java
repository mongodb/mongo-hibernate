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

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import org.bson.BsonDocument;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;

final class MongoBatchPreparedStatement extends MongoPreparedStatement {

    private final List<BsonDocument> commandBatch;
    private final BsonDocument commandPrototype;

    private final String collectionName;
    private final String commandName;

    public MongoBatchPreparedStatement(
            MongoClient mongoClient,
            ClientSession clientSession,
            MongoConnection mongoConnection,
            String mql,
            int batchSize)
            throws SQLException {
        super(mongoClient, clientSession, mongoConnection, mql);
        this.commandPrototype = parse(mql);
        BsonDocument command = commandPrototype.clone();
        setCommand(command);
        commandName = assertNotNull(command.getFirstKey());
        collectionName = assertNotNull(command.getString(commandName).getValue());
        commandBatch = new ArrayList<>(batchSize);
    }

    @Override
    public void addBatch() throws SQLException {
        checkClosed();
        commandBatch.add(assertNotNull(getCommand()));
        setCommand(commandPrototype.clone());
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        commandBatch.clear();
        setCommand(commandPrototype.clone());
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkClosed();
        assertTrue(!commandBatch.isEmpty());
        return bulkWrite();
    }

    private int[] bulkWrite() throws SQLException {
        try {
            var writeModels = new ArrayList<WriteModel<BsonDocument>>(commandBatch.size());
            for (BsonDocument command : commandBatch) {
                switch (assertNotNull(commandName)) {
                    case "insert":
                        writeModels.add(new InsertOneModel<>(command));
                        break;
                    case "update":
                        BsonDocument filter =
                                command.getArray("updates").get(0).asDocument().getDocument("q");
                        writeModels.add(new UpdateOneModel<>(filter, command));
                        break;
                    case "delete":
                        writeModels.add(new DeleteOneModel<>(command));
                        break;
                    default:
                        throw new SQLFeatureNotSupportedException("Unrecognized bulk writing command: " + commandName);
                }
            }
            getMongoDatabase()
                    .getCollection(assertNotNull(collectionName), BsonDocument.class)
                    .bulkWrite(writeModels);
            var rowCounts = new int[commandBatch.size()];
            Arrays.fill(rowCounts, MongoBatchPreparedStatement.SUCCESS_NO_INFO);
            return rowCounts;
        } catch (RuntimeException e) {
            throw new SQLException("Failed to run bulk operation: " + e.getMessage(), e);
        }
    }
}

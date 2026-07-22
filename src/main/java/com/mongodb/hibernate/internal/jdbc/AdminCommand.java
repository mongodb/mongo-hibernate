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
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;

abstract sealed class AdminCommand permits AdminCommand.CreateIndexesCommand, AdminCommand.DropIndexesCommand {
    private static <T> Decoder<List<T>> listOf(Decoder<T> inner) {
        return (reader, decoderContext) -> {
            var results = new ArrayList<T>();
            reader.readStartArray();
            while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                results.add(inner.decode(reader, decoderContext));
            }
            reader.readEndArray();
            return results;
        };
    }

    static IndexModel decodeIndexModel(BsonReader reader, DecoderContext decoderContext) {
        reader.readStartDocument();
        reader.readName("key");
        var key = new BsonDocumentCodec().decode(reader, decoderContext);
        var name = reader.readString("name");
        var unique = reader.readBoolean("unique");
        final var result = new IndexModel(key, new IndexOptions().name(name).unique(unique));
        reader.readEndDocument();
        return result;
    }

    public static AdminCommand decode(BsonReader reader, DecoderContext decoderContext)
            throws SQLFeatureNotSupportedException {
        reader.readStartDocument();
        final var name = reader.readName();
        final var result =
                switch (name) {
                    case "createIndexes" -> {
                        final var collectionName = reader.readString();
                        reader.readName("indexes");
                        final var indexes =
                                listOf(AdminCommand::decodeIndexModel).decode(reader, decoderContext);
                        yield new CreateIndexesCommand(collectionName, indexes);
                    }
                    case "dropIndexes" -> {
                        final var collectionName = reader.readString();
                        yield new DropIndexesCommand(collectionName, reader.readString("index"));
                    }
                    default ->
                        throw new SQLFeatureNotSupportedException(
                                "Cannot decode command %s: unknown command".formatted(name));
                };
        reader.readEndDocument();
        return result;
    }

    abstract void execute(MongoDatabase database);

    static final class CreateIndexesCommand extends AdminCommand {
        private final String collectionName;
        private final List<IndexModel> indexes;

        CreateIndexesCommand(String collectionName, List<IndexModel> indexes) {
            this.collectionName = collectionName;
            this.indexes = indexes;
        }

        @Override
        void execute(MongoDatabase database) {
            database.getCollection(collectionName).createIndexes(indexes);
        }
    }

    static final class DropIndexesCommand extends AdminCommand {
        private final String collectionName;
        private final String index;

        DropIndexesCommand(String collectionName, String index) {
            this.collectionName = collectionName;
            this.index = index;
        }

        @Override
        void execute(MongoDatabase database) {
            database.getCollection(collectionName).dropIndex(index);
        }
    }
}

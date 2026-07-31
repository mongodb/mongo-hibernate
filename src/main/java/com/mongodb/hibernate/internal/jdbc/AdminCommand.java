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

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.bson.BsonDocument;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;

abstract sealed class AdminCommand
        permits AdminCommand.CreateIndexesCommand,
                AdminCommand.CreateCollectionCommand,
                AdminCommand.DropCollectionCommand {
    private static <T, R> Decoder<List<R>> listOf(Decoder<T> inner, Function<T, R> mapper) {
        return (reader, decoderContext) -> {
            var results = new ArrayList<R>();
            reader.readStartArray();
            while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                results.add(mapper.apply(inner.decode(reader, decoderContext)));
            }
            reader.readEndArray();
            return results;
        };
    }

    private static final Decoder<List<IndexModel>> INDEX_LIST =
            listOf(MongoClientSettings.getDefaultCodecRegistry().get(Index.class), Index::intoIndexModel);

    public record Index(String name, BsonDocument key, boolean unique) {
        IndexModel intoIndexModel() {
            return new IndexModel(key, new IndexOptions().name(name).unique(unique));
        }
    }

    public static AdminCommand toAdminCommand(BsonReader reader, DecoderContext decoderContext)
            throws SQLFeatureNotSupportedException {
        reader.readStartDocument();
        var name = reader.readName();
        final var result =
                switch (name) {
                    case "create" -> new CreateCollectionCommand(reader.readString());
                    case "createIndexes" -> {
                        var collectionName = reader.readString();
                        reader.readName("indexes");
                        yield new CreateIndexesCommand(collectionName, INDEX_LIST.decode(reader, decoderContext));
                    }
                    case "drop" -> new DropCollectionCommand(reader.readString());
                    default ->
                        throw new SQLFeatureNotSupportedException(
                                "Cannot decode command %s: unknown command".formatted(name));
                };
        reader.readEndDocument();
        return result;
    }

    abstract void execute(MongoDatabase database);

    static final class CreateCollectionCommand extends AdminCommand {

        private final String collectionName;

        CreateCollectionCommand(String collectionName) {
            this.collectionName = collectionName;
        }

        @Override
        void execute(MongoDatabase database) {
            database.createCollection(collectionName);
        }
    }

    static final class DropCollectionCommand extends AdminCommand {

        private final String collectionName;

        DropCollectionCommand(String collectionName) {
            this.collectionName = collectionName;
        }

        @Override
        void execute(MongoDatabase database) {
            database.getCollection(collectionName).drop();
        }
    }

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
}

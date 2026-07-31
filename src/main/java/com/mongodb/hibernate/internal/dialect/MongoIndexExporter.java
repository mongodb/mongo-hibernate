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

package com.mongodb.hibernate.internal.dialect;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.MongoConstants;
import java.util.List;
import java.util.stream.Stream;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.hibernate.AnnotationException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.Exportable;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.Exporter;

abstract class MongoIndexExporter<T extends Exportable> implements Exporter<T> {

    private final boolean unique;

    protected MongoIndexExporter(boolean unique) {
        this.unique = unique;
    }

    private static String createIndex(String collectionName, BsonDocument keys, String indexName, boolean unique) {
        var command = new BsonDocument(List.of(
                new BsonElement("createIndexes", new BsonString(collectionName)),
                new BsonElement(
                        "indexes",
                        new BsonArray(List.of(new BsonDocument(List.of(
                                new BsonElement("key", keys),
                                new BsonElement("name", new BsonString(indexName)),
                                new BsonElement("unique", BsonBoolean.valueOf(unique)))))))));
        // This intentionally looks like a Mongo command, but it is parsed by AdminCommand and is not sent directly to
        // the server
        return command.toJson(MongoConstants.EXTENDED_JSON_WRITER_SETTINGS);
    }

    protected abstract String indexNameForExportable(T exportable);

    protected abstract Stream<IndexEntry> indexEntriesForExportable(T exportable);

    protected abstract Table tableForExportable(T exportable);

    @Override
    public final String[] getSqlCreateStrings(T exportable, Metadata metadata, SqlStringGenerationContext context) {
        var table = tableForExportable(exportable);
        var collectionName = context.format(table.getQualifiedTableName());
        var keys = new BsonDocument();
        indexEntriesForExportable(exportable).forEach(e -> e.insert(keys, table));
        var indexName = indexNameForExportable(exportable);
        if (!optionsForExportable(exportable).isBlank()) {
            throw new FeatureNotSupportedException(
                    "Index %s on %s has options, which is not supported".formatted(indexName, collectionName));
        }

        return new String[] {createIndex(collectionName, keys, indexName, unique)};
    }

    protected abstract String optionsForExportable(T exportable);

    @Override
    public final String[] getSqlDropStrings(T exportable, Metadata metadata, SqlStringGenerationContext context) {
        throw new IllegalStateException(
                "HIBERNATE-66: Dropping indices was deemed something that Hibernate never called");
    }

    protected record IndexEntry(String name, String direction) {
        void insert(BsonDocument document, Table table) {
            if (table.getColumn(new Identifier(name, false)) == null) {
                throw new AnnotationException("Table '%s' has no column named '%s'".formatted(table.getName(), name));
            }
            int directionValue;
            if (direction.isBlank() || direction.equals("asc")) {
                directionValue = 1;
            } else if (direction.equals("desc")) {
                directionValue = -1;
            } else {
                throw new AnnotationException("Unknown order %s for column %s".formatted(direction, name));
            }
            document.put(name, new BsonInt32(directionValue));
        }
    }
}

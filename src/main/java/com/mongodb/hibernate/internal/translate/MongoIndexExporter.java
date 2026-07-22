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

package com.mongodb.hibernate.internal.translate;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.MongoConstants;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonNumber;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.Exportable;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.Exporter;

public abstract class MongoIndexExporter<T extends Exportable> implements Exporter<T> {

    private final boolean unique;

    protected MongoIndexExporter(boolean unique) {
        this.unique = unique;
    }

    /**
     * Convenience method to generate an index name from the set of fields it is over.
     *
     * @return a string representation of this index's fields
     */
    public static String generateIndexName(final BsonDocument index) {
        StringBuilder indexName = new StringBuilder();
        for (final String keyNames : index.keySet()) {
            if (!indexName.isEmpty()) {
                indexName.append('_');
            }
            indexName.append(keyNames).append('_');
            BsonValue ascOrDescValue = index.get(keyNames);
            if (ascOrDescValue instanceof BsonNumber number) {
                indexName.append(number.intValue());
            } else if (ascOrDescValue instanceof BsonString str) {
                indexName.append(str.getValue().replace(' ', '_'));
            }
        }
        return indexName.toString();
    }

    protected abstract Table tableForExportable(T exportable);

    protected abstract Optional<String> indexNameForExportable(T exportable);

    protected abstract Stream<BsonElement> keysForExportable(T exportable);

    @Override
    public final String[] getSqlCreateStrings(T exportable, Metadata metadata, SqlStringGenerationContext context) {
        final var collectionName = tableForExportable(exportable).getName();
        final var keys = new BsonDocument();
        keysForExportable(exportable).forEach(e -> keys.put(e.getName(), e.getValue()));
        final var indexName = indexNameForExportable(exportable).orElseGet(() -> generateIndexName(keys));
        if (!optionsForExportable(exportable).isBlank()) {
            throw new FeatureNotSupportedException(
                    "Index %s on %s has options, which is not supported".formatted(indexName, collectionName));
        }
        final var command = new BsonDocument(List.of(
                new BsonElement("createIndexes", new BsonString(collectionName)),
                new BsonElement(
                        "indexes",
                        new BsonArray(List.of(new BsonDocument(List.of(
                                new BsonElement("key", keys),
                                new BsonElement("name", new BsonString(indexName)),
                                new BsonElement("unique", BsonBoolean.valueOf(unique)))))))));
        // This intentionally looks like a Mongo command, but it is parsed by AdminCommand and is not sent directly to
        // the server
        return new String[] {command.toJson(MongoConstants.EXTENDED_JSON_WRITER_SETTINGS)};
    }

    protected abstract String optionsForExportable(T exportable);

    @Override
    public final String[] getSqlDropStrings(T exportable, Metadata metadata, SqlStringGenerationContext context) {
        final var collectionName = tableForExportable(exportable).getName();
        final var indexName = indexNameForExportable(exportable)
                .orElseGet(() -> generateIndexName(
                        new BsonDocument(keysForExportable(exportable).toList())));
        final var command = new BsonDocument(List.of(
                new BsonElement("dropIndexes", new BsonString(collectionName)),
                new BsonElement("index", new BsonString(indexName))));
        // This intentionally looks like a Mongo command, but it is parsed by AdminCommand and is not sent directly to
        // the server
        return new String[] {command.toJson(MongoConstants.EXTENDED_JSON_WRITER_SETTINGS)};
    }
}

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
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonInt32;
import org.bson.BsonNumber;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.Exportable;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.Exporter;

public abstract class MongoIndexExporter<T extends Exportable> implements Exporter<T> {

    private static final Pattern COLUMN_DESCRIPTOR =
            Pattern.compile("^([^ ]+)(?: +(asc|desc) *)?", Pattern.CASE_INSENSITIVE);
    private final boolean unique;

    protected MongoIndexExporter(boolean unique) {
        this.unique = unique;
    }

    /**
     * Convenience method to generate an index name from the set of fields it is over.
     *
     * @return a string representation of this index's fields
     */
    public static String generateIndexName(BsonDocument index) {
        StringBuilder indexName = new StringBuilder();
        for (String keyNames : index.keySet()) {
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

    protected abstract Stream<String> indexEntriesForExportable(T exportable);

    private BsonDocument generateKeys(T exportable) {
        var keys = new BsonDocument();
        indexEntriesForExportable(exportable).forEach(indexEntry -> {
            var match = COLUMN_DESCRIPTOR.matcher(indexEntry);
            if (!match.matches()) {
                throw new IllegalArgumentException("Invalid index entry format: " + indexEntry);
            }
            keys.put(
                    match.group(1),
                    new BsonInt32(
                            Objects.requireNonNullElse(match.group(2), "asc").equalsIgnoreCase("asc") ? 1 : -1));
        });
        return keys;
    }

    @Override
    public final String[] getSqlCreateStrings(T exportable, Metadata metadata, SqlStringGenerationContext context) {
        var collectionName = tableForExportable(exportable).getName();
        var keys = generateKeys(exportable);
        var indexName = indexNameForExportable(exportable).orElseGet(() -> generateIndexName(keys));
        if (!optionsForExportable(exportable).isBlank()) {
            throw new FeatureNotSupportedException(
                    "Index %s on %s has options, which is not supported".formatted(indexName, collectionName));
        }
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
        return new String[] {command.toJson(MongoConstants.EXTENDED_JSON_WRITER_SETTINGS)};
    }

    protected abstract String optionsForExportable(T exportable);

    @Override
    public final String[] getSqlDropStrings(T exportable, Metadata metadata, SqlStringGenerationContext context) {
        throw new IllegalStateException(
                "HIBERNATE-66: Dropping indices was deemed something that Hibernate never called");
    }
}

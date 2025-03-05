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

package com.mongodb.hibernate.internal.service;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PersistentClass;

public class MongoAdditionalMappingContributor implements AdditionalMappingContributor {
    private static final String ID_FIELD = "_id";

    @Override
    public void contribute(
            AdditionalMappingContributions contributions,
            InFlightMetadataCollector metadata,
            ResourceStreamLocator resourceStreamLocator,
            MetadataBuildingContext buildingContext) {
        metadata.getEntityBindings().forEach(MongoAdditionalMappingContributor::setIdentifierColumnName);
    }

    private static void setIdentifierColumnName(PersistentClass persistentClass) {
        var identifier = persistentClass.getIdentifier();
        if (!(identifier instanceof BasicValue)) {
            throw new FeatureNotSupportedException("MongoDB doesn't support '_id' field spanning multiple columns");
        }
        assertTrue(identifier.getColumns().size() == 1);
        var idColumn = assertNotNull(identifier.getColumns().get(0));
        idColumn.setName(ID_FIELD);
    }
}

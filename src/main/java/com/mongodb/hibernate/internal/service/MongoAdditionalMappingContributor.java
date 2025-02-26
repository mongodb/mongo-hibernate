package com.mongodb.hibernate.internal.service;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PersistentClass;

import java.util.Set;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;

public class MongoAdditionalMappingContributor implements AdditionalMappingContributor {
    private static final String ID_FIELD = "_id";

    @Override
    public void contribute(
            AdditionalMappingContributions contributions,
            InFlightMetadataCollector metadata,
            ResourceStreamLocator resourceStreamLocator,
            MetadataBuildingContext buildingContext) {
        var mongoImplicitNamingStrategy = (MongoMetadataBuilderFactory.MongoImplicitNamingStrategy) buildingContext.getBuildingOptions().getImplicitNamingStrategy();
        metadata.getEntityBindings().forEach(persistentClass -> setIdentifierColumnName(persistentClass, mongoImplicitNamingStrategy.getEntitiesWithImplicitIdentifier()));
    }

    private static void setIdentifierColumnName(PersistentClass persistentClass, Set<String> entitiesWithImplicitIdentifier) {
        var identifier = persistentClass.getIdentifier();
        if (!(identifier instanceof BasicValue)) {
            throw new FeatureNotSupportedException("MongoDB doesn't support '_id' field spanning multiple columns");
        }
        assertTrue(identifier.getColumns().size() == 1);
        var idColumn = assertNotNull(identifier.getColumns().get(0));
        if (!entitiesWithImplicitIdentifier.contains(persistentClass.getEntityName()) && !idColumn.getName().equals(ID_FIELD)) {
            throw new FeatureNotSupportedException(String.format("MongoDB id field name has to be '_id' (configured explicitly: %s)", idColumn.getName()));
        }
        idColumn.setName(ID_FIELD);
    }
}

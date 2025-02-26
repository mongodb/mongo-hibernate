package com.mongodb.hibernate.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.hibernate.cfg.Configuration;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
                MongoIdFieldNameIntegrationTests.EntityWithoutIdColumnAnnotation.class,
                MongoIdFieldNameIntegrationTests.EntityWithIdColumnAnnotationWithoutNameProperty.class,
                MongoIdFieldNameIntegrationTests.EntityWithIdColumnAnnotationWithNamePropertyIdentical.class
        })
class MongoIdFieldNameIntegrationTests {

    @BeforeEach
    void setUp() {
        onMongoCollection("movies", MongoCollection::drop);
    }

    @Test
    void testEntityWithoutIdColumnAnnotation(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithoutIdColumnAnnotation();
            movie.id = 1;
            session.persist(movie);
        });
    }

    @Test
    void testEntityWithIdColumnAnnotationWithoutNameProperty(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithIdColumnAnnotationWithoutNameProperty();
            movie.id = 1;
            session.persist(movie);
        });
    }

    @Test
    void testEntityWithIdColumnAnnotationWithNamePropertyIdentical(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var movie = new EntityWithIdColumnAnnotationWithNamePropertyIdentical();
            movie.id = 1;
            session.persist(movie);
        });
    }

    // TO-DO-HIBERNATE-56 https://jira.mongodb.org/browse/HIBERNATE-56
    // add testing case for the case that id column is explict specified and different from '_id'

    @Entity(name = "Entity1")
    @Table(name = "movies")
    static class EntityWithoutIdColumnAnnotation {
        @Id
        int id;
    }

    @Entity(name = "Entity2")
    @Table(name = "movies")
    static class EntityWithIdColumnAnnotationWithoutNameProperty {
        @Id
        @Column
        int id;
    }

    @Entity(name = "Entity4")
    @Table(name = "movies")
    static class EntityWithIdColumnAnnotationWithNamePropertyIdentical {
        @Id
        @Column(name = "_id")
        int id;
    }

    private void onMongoCollection(String collectionName, Consumer<MongoCollection<BsonDocument>> collectionConsumer) {
        var connectionString = new ConnectionString(new Configuration().getProperty(JAKARTA_JDBC_URL));
        try (var mongoClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build())) {
            var collection = mongoClient
                    .getDatabase(connectionString.getDatabase())
                    .getCollection(collectionName, BsonDocument.class);
            collectionConsumer.accept(collection);
        }
    }
}


package com.mongodb.hibernate.embeddable;

import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
                EmptyStructIntegrationTests.StructHolder.class,
                EmptyStructIntegrationTests.EmptyStruct.class
        })
@ExtendWith(MongoExtension.class)
class EmptyStructIntegrationTests {


    @Test
    void test(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var holder = new StructHolder();
            holder.id = 1;
            holder.emptyStruct = new EmptyStruct();
            session.persist(holder);
        });
    }

    @Entity
    @Table(name = "collection")
    static class StructHolder {
        @Id
        int id;
        EmptyStruct emptyStruct;
    }

    @Embeddable
    @Struct(name = "EmptyStruct")
    static class EmptyStruct {
        // No fields
    }
}

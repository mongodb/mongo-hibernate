package com.mongodb.hibernate.query.association.onetoone;

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.Test;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;

@DomainModel(annotatedClasses = {
    SimpleOneToOneIntegrationTests.User.class,
    SimpleOneToOneIntegrationTests.Profile.class
})
class SimpleOneToOneIntegrationTests extends AbstractQueryIntegrationTests {

    @Test
    void test() {
        var profile = new Profile(1, "John Doe", 30);
        var user = new User(1, profile);
        profile.user = user;
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(profile);
            session.persist(user);
        });
        var loadedUser = getSessionFactoryScope().fromTransaction(session -> session.find(User.class, user.id));
        assertEq(user, loadedUser);
    }

    @Entity
    @Table(name = "users")
    static class User {
        @Id
        int id;

        @OneToOne
        Profile profile;

        User() {}
        User(int id, Profile profile) {
            this.id = id;
            this.profile = profile;
        }
    }

    @Entity
    @Table(name = "profiles")
    static class Profile {
        @Id
        int id;

        String name;
        int age;

        @OneToOne(mappedBy = "profile")
        User user;

        Profile() {}

        Profile(int id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

    }
}

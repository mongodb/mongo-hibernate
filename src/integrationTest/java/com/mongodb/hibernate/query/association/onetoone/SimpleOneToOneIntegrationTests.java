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

package com.mongodb.hibernate.query.association.onetoone;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {SimpleOneToOneIntegrationTests.User.class, SimpleOneToOneIntegrationTests.Profile.class})
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

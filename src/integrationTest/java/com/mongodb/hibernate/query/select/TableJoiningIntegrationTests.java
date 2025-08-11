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

package com.mongodb.hibernate.query.select;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            TableJoiningIntegrationTests.Country.class,
            TableJoiningIntegrationTests.Province.class,
            TableJoiningIntegrationTests.City.class
        })
class TableJoiningIntegrationTests extends AbstractQueryIntegrationTests {

    @Test
    void testOneLevelJoin() {
        var canada = new Country("CA", "Canada");
        var ontario = new Province("ON", "Ontario", canada);
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(canada);
            session.persist(ontario);
        });
        var loadedOntario =
                getSessionFactoryScope().fromTransaction(session -> session.find(Province.class, ontario.abbr));
        assertEq(ontario, loadedOntario);
    }

    @Test
    void testMultipleLevelsJoin() {
        var canada = new Country("CA", "Canada");
        var ontario = new Province("ON", "Ontario", canada);
        var toronto = new City(1, "Toronto", ontario);
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(canada);
            session.persist(ontario);
            session.persist(toronto);
        });
        var loadedToronto = getSessionFactoryScope().fromTransaction(session -> session.find(City.class, toronto.id));
        assertEq(toronto, loadedToronto);
    }

    @Entity(name = "City")
    @Table(name = "cities")
    static class City {
        @Id
        int id;

        String name;

        @ManyToOne
        @Fetch(FetchMode.JOIN)
        Province province;

        City() {}

        City(int id, String name, Province province) {
            this.id = id;
            this.name = name;
            this.province = province;
        }
    }

    @Entity(name = "Province")
    @Table(name = "provinces")
    static class Province {
        @Id
        String abbr;

        String name;

        @ManyToOne
        @Fetch(FetchMode.JOIN)
        Country country;

        public Province() {}

        Province(String abbr, String name, Country country) {
            this.abbr = abbr;
            this.name = name;
            this.country = country;
        }

        @Override
        public String toString() {
            return "Province{" + "abbr='" + abbr + '\'' + ", country=" + country + '}';
        }
    }

    @Entity(name = "Country")
    @Table(name = "countries")
    static class Country {
        @Id
        String code;

        String name;

        public Country() {}

        Country(String code, String name) {
            this.code = code;
            this.name = name;
        }

        @Override
        public String toString() {
            return "Country{" + "code='" + code + '\'' + '}';
        }
    }
}

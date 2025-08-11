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

package com.mongodb.hibernate.query.association.onetomany;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Set;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            SimpleOneToManyIntegrationTests.Company.class,
            SimpleOneToManyIntegrationTests.Department.class
        })
public class SimpleOneToManyIntegrationTests extends AbstractQueryIntegrationTests {

    @Test
    @Disabled("OneToMany not implemented yet")
    void test() {
        var company = new Company(1, "Acme Corp");
        var dept1 = new Department(1, "HR", company);
        var dept2 = new Department(2, "Engineering", company);
        company.departments = Set.of(dept1, dept2);

        getSessionFactoryScope().inTransaction(session -> {
            session.persist(company);
            session.persist(dept1);
            session.persist(dept2);
        });
        getSessionFactoryScope().inTransaction(session -> {
            var loadedCompany = session.find(Company.class, company.id);
            assertEq(company, loadedCompany);
        });
    }

    @Entity(name = "Department")
    @Table(name = "departments")
    static class Department {
        @Id
        int id;

        String name;

        @ManyToOne
        Company company;

        Department() {}

        Department(int id, String name, Company company) {
            this.id = id;
            this.name = name;
            this.company = company;
        }
    }

    @Entity(name = "Company")
    @Table(name = "companies")
    static class Company {
        @Id
        int id;

        String name;

        @OneToMany(mappedBy = "company")
        Set<Department> departments;

        Company() {}

        Company(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "Company{" + "id=" + id + ", name='" + name + '\'' + '}';
        }
    }
}

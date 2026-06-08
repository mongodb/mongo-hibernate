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

package com.mongodb.hibernate.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// The @SpringBootApplication lives in this package — NOT com.mongodb.hibernate.autoconfigure —
// so its component scan does not pick up the production auto-configuration classes (which would
// otherwise be registered both by scanning and by the auto-configuration imports). With
// spring.jpa.database-platform=MongoDB set (in application.properties), the auto-configuration
// activates and borrows the Spring-managed MongoClient (created from spring.mongodb.uri) to wire
// the whole stack.
@SpringBootTest
class MongoHibernateSpringBootIntegrationTests {

    private static final List<String> COMMAND_NAMES = new CopyOnWriteArrayList<>();

    private static final CommandListener RECORDING_LISTENER = new CommandListener() {
        @Override
        public void commandStarted(CommandStartedEvent event) {
            COMMAND_NAMES.add(event.getCommandName());
        }
    };

    @SpringBootApplication
    static class TestApplication {

        @Bean
        MongoClientSettingsBuilderCustomizer recordingCommandListenerCustomizer() {
            return builder -> builder.addCommandListener(RECORDING_LISTENER);
        }
    }

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    JpaTransactionManager transactionManager;

    @Autowired
    BookRepository bookRepository;

    @AfterEach
    void cleanUp() {
        bookRepository.deleteAll();
    }

    @Test
    void entityManagerFactoryAndTransactionManagerAreCreated() {
        assertThat(entityManagerFactory).isNotNull();
        assertThat(transactionManager).isNotNull();
    }

    @Test
    void entityCanBePersistedAndFound() {
        var id = new ObjectId();
        var book = new TestBook(id, "The Hobbit");

        var txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> bookRepository.save(book));

        var found = bookRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().title).isEqualTo("The Hobbit");
    }

    @Test
    void jpaRepositoryBeanIsCreated() {
        assertThat(bookRepository).isNotNull();
    }

    @Test
    void rolledBackTransactionPersistsNothing() {
        var id = new ObjectId();
        var txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            bookRepository.save(new TestBook(id, "Rolled back"));
            status.setRollbackOnly();
            return null;
        });
        assertThat(bookRepository.findById(id)).isEmpty();
    }

    @Test
    void entityManagerOperationsUseTheBorrowedSpringClient() {
        // The command listener is registered only on the spring-boot-mongodb client's settings. A Hibernate
        // operation triggering it proves Hibernate is using that same (borrowed) client, not a second one.
        COMMAND_NAMES.clear();
        var txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> bookRepository.findAll());
        assertThat(COMMAND_NAMES).contains("aggregate");
    }
}

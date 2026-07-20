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

package com.mongodb.hibernate.junit;

import static java.lang.String.format;
import static org.junit.Assert.assertTrue;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotatedFields;
import static org.junit.platform.commons.util.ReflectionUtils.isStatic;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * Test classes run concurrently (JUnit parallel execution, classes-concurrent / methods-same-thread), so each top-level
 * test class gets its own database; nested classes share their top-level class's database. ({@code SessionFactory}
 * instances are not shared — the testing framework builds one per test method.) The database is dropped before each
 * test and after all tests of the class.
 */
public final class MongoExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, InvocationInterceptor {

    private static final State STATE = State.create();

    /**
     * A {@link MongoConfigurationContributor} that points a {@code SessionFactory} at {@code testClass}'s
     * {@linkplain #databaseNameFor database} and installs the {@link TestCommandListener}. Used by
     * {@link MongoServiceRegistryProducer} and by tests that bootstrap Hibernate directly, so every test is isolated to
     * its own database without any per-thread state.
     */
    public static MongoConfigurationContributor configurationContributorForClass(Class<?> testClass) {
        var databaseName = databaseNameFor(testClass);
        return configurator -> configurator
                .databaseName(databaseName)
                .applyToMongoClientSettings(
                        builder -> builder.addCommandListener(STATE.commandListenerByDatabase.computeIfAbsent(
                                databaseName, ignored -> new TestCommandListener())));
    }

    /** Gets the MongoDB commands initiated by the currently-executing test in this test class. */
    public static List<BsonDocument> getCommands(Class<?> testClass) {
        return STATE.commandListenerByDatabase.get(databaseNameFor(testClass)).getCommands();
    }

    /**
     * Clear the MongoDB commands initiated so far by this test class. Required only in special cases when the test
     * initiates one or more commands that it does not want to assert on.
     */
    public static void clearCommands(Class<?> testClass) {
        STATE.commandListenerByDatabase.get(databaseNameFor(testClass)).clear();
    }

    /** Injects the {@linkplain InjectMongoClient client}, {@linkplain InjectMongoCollection collections}. */
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var fieldMustBeStaticMsgFormat = "The field [%s] must be static";
        for (var field : findAnnotatedFields(context.getRequiredTestClass(), InjectMongoClient.class)) {
            assertTrue(format(fieldMustBeStaticMsgFormat, field), isStatic(field));
            field.setAccessible(true);
            field.set(null, STATE.mongoClient());
        }
        for (var field : findAnnotatedFields(context.getRequiredTestClass(), InjectMongoCollection.class)) {
            assertTrue(format(fieldMustBeStaticMsgFormat, field), isStatic(field));
            var annotation = field.getDeclaredAnnotation(InjectMongoCollection.class);
            var collectionName = annotation.value();
            var mongoCollection = currentDatabase(context).getCollection(collectionName, BsonDocument.class);
            field.setAccessible(true);
            field.set(null, mongoCollection);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        currentDatabase(context).drop();
    }

    /** Empties every {@linkplain InjectMongoCollection collection} in the class's database before each test. */
    @Override
    public void beforeEach(ExtensionContext context) {
        clearDatabase(context);
    }

    /**
     * Clears the class's captured commands just before each test body runs — after any {@code @BeforeEach} seeding — so
     * a test that does not clear explicitly still sees only its own commands. {@code @Test} methods route through
     * {@link #interceptTestMethod} and {@code @ParameterizedTest}/{@code @RepeatedTest} through
     * {@link #interceptTestTemplateMethod}, so both are overridden. Tests needing a finer boundary (mid-method) still
     * call {@link #clearCommands} explicitly.
     */
    @Override
    public void interceptTestMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext)
            throws Throwable {
        clearCommandsBeforeTestBody(extensionContext);
        invocation.proceed();
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext)
            throws Throwable {
        clearCommandsBeforeTestBody(extensionContext);
        invocation.proceed();
    }

    private static void clearCommandsBeforeTestBody(ExtensionContext context) {
        // A missing listener means no SessionFactory was built for this class yet, so nothing was captured — clearing
        // is a legitimate no-op here.
        var commandListener = STATE.commandListenerByDatabase.get(databaseNameFor(context));
        if (commandListener != null) {
            commandListener.clear();
        }
    }

    private static String databaseNameFor(ExtensionContext context) {
        return databaseNameFor(context.getRequiredTestClass());
    }

    /// The database for `testClass`: a unique name assigned once per top-level class and memoized, so a class and
    /// its nested classes share one database. Used to point each test's `SessionFactory` at the same database this
    /// extension drops, without depending on any per-thread state.
    private static String databaseNameFor(Class<?> testClass) {
        return STATE.databaseByTopLevelClass.computeIfAbsent(
                topLevelClass(testClass),
                ignored -> STATE.baseDatabaseName() + "_" + STATE.databaseCounter.incrementAndGet());
    }

    private static Class<?> topLevelClass(Class<?> testClass) {
        var current = testClass;
        while (current.getEnclosingClass() != null) {
            current = current.getEnclosingClass();
        }
        return current;
    }

    private static MongoDatabase currentDatabase(ExtensionContext context) {
        return STATE.mongoClient().getDatabase(databaseNameFor(context));
    }

    // Clear data by emptying each collection rather than dropDatabase(): dropDatabase is a ~125ms catalog operation,
    // while deleteMany is ordinary CRUD (~sub-ms). Collections/indexes persist (harmless; tests re-seed).
    private static void clearDatabase(ExtensionContext context) {
        var database = currentDatabase(context);
        for (var collectionName : database.listCollectionNames()) {
            database.getCollection(collectionName).deleteMany(new BsonDocument());
        }
    }

    private record State(
            AtomicInteger databaseCounter,
            Map<Class<?>, String> databaseByTopLevelClass,
            Map<String, TestCommandListener> commandListenerByDatabase,
            MongoClient mongoClient,
            String baseDatabaseName) {
        static State create() {
            @SuppressWarnings("unchecked")
            var hibernateProperties = (Map<String, Object>) (Map<?, Object>) new Configuration().getProperties();
            var mongoConfig = new MongoConfigurationBuilder(hibernateProperties).build();
            var mongoClient = MongoClients.create(mongoConfig.mongoClientSettings());

            var state = new State(
                    new AtomicInteger(),
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>(),
                    mongoClient,
                    mongoConfig.databaseName());
            Runtime.getRuntime().addShutdownHook(new Thread(state::close));
            return state;
        }

        private void close() {
            mongoClient.close();
        }
    }

    private static final class TestCommandListener implements CommandListener {

        private final List<BsonDocument> commands = new ArrayList<>();

        @Override
        public void commandStarted(CommandStartedEvent event) {
            commands.add(event.getCommand().clone());
        }

        private List<BsonDocument> getCommands() {
            return List.copyOf(commands);
        }

        private void clear() {
            commands.clear();
        }
    }
}

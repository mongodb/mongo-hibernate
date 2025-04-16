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

package com.mongodb.hibernate.internal;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.hibernate.service.Service;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MongoTestCommandListener implements CommandListener, Service {
    @Serial
    private static final long serialVersionUID = 1L;

    static MongoTestCommandListener INSTANCE = new MongoTestCommandListener();

    private final List<BsonDocument> succeededCommands = new ArrayList<>();
    private final List<BsonDocument> failedCommands = new ArrayList<>();
    private final Map<Long, BsonDocument> startedCommands = new HashMap<>();

    private MongoTestCommandListener() {}

    @Override
    public synchronized void commandStarted(CommandStartedEvent event) {
        startedCommands.put(event.getOperationId(), event.getCommand().clone());
    }

    @Override
    public synchronized void commandSucceeded(CommandSucceededEvent event) {
        succeededCommands.add(getStartedCommand(event.getOperationId()));
    }

    @Override
    public synchronized void commandFailed(CommandFailedEvent event) {
        failedCommands.add(getStartedCommand(event.getOperationId()));
    }

    private BsonDocument getStartedCommand(long operationId) {
        return assertNotNull(startedCommands.get(operationId));
    }

    public synchronized boolean areAllCommandsFinishedAndSucceeded() {
        return succeededCommands.size() == startedCommands.size() && failedCommands.isEmpty();
    }

    public synchronized List<BsonDocument> getSucceededCommands() {
        return List.copyOf(succeededCommands);
    }

    public synchronized void clear() {
        startedCommands.clear();
        succeededCommands.clear();
        failedCommands.clear();
    }
}

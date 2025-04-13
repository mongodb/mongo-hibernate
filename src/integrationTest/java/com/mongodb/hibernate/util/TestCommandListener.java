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

package com.mongodb.hibernate.util;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import com.mongodb.hibernate.internal.MongoAssertions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TestCommandListener implements CommandListener {

    private final List<BsonDocument> finishedCommands = new ArrayList<>();

    private final Map<Long, BsonDocument> startedCommands = new HashMap<>();

    @Override
    public void commandStarted(CommandStartedEvent event) {
        startedCommands.put(event.getOperationId(), event.getCommand().clone());
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        var startedCommand = assertNotNull(startedCommands.remove(event.getOperationId()));
        finishedCommands.add(startedCommand);
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        throw MongoAssertions.fail();
    }

    public List<BsonDocument> getFinishedCommands() {
        return List.copyOf(finishedCommands);
    }

    public void clear() {
        finishedCommands.clear();
    }

    public static BsonDocument getActualAggregateCommand(BsonDocument command) {
        var actualCommand = new BsonDocument();
        actualCommand.put("aggregate", command.getString("aggregate"));
        actualCommand.put("pipeline", command.getArray("pipeline"));
        return actualCommand;
    }
}

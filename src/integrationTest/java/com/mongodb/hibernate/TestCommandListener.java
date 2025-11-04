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

package com.mongodb.hibernate;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.hibernate.service.Service;

public final class TestCommandListener implements CommandListener, Service {
    @Serial
    private static final long serialVersionUID = 1L;

    static TestCommandListener INSTANCE = new TestCommandListener();

    private final transient List<BsonDocument> startedCommands = new ArrayList<>();

    private TestCommandListener() {}

    @Override
    public synchronized void commandStarted(CommandStartedEvent event) {
        startedCommands.add(event.getCommand().clone());
    }

    public synchronized List<BsonDocument> getStartedCommands() {
        return List.copyOf(startedCommands);
    }

    public synchronized void clear() {
        startedCommands.clear();
    }
}

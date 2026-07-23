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

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;

public final class TestCommandListener implements CommandListener {

    private final List<BsonDocument> commands = new ArrayList<>();

    TestCommandListener() {}

    @Override
    public void commandStarted(CommandStartedEvent event) {
        commands.add(event.getCommand().clone());
    }

    public List<BsonDocument> getCommands() {
        return List.copyOf(commands);
    }

    public void clear() {
        commands.clear();
    }
}

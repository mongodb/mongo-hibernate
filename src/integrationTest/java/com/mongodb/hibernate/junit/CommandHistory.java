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

import java.util.List;
import org.bson.BsonDocument;

/**
 * The commands a test's {@code SessionFactory} has sent to MongoDB, in order. This is the test-facing view of
 * {@link TestCommandListener}: tests read the recorded commands and reset the record, without seeing the driver
 * {@link com.mongodb.event.CommandListener} machinery that populates it. Injected with {@link InjectCommandHistory}.
 */
public interface CommandHistory {

    /** The commands recorded so far, in the order they were sent. */
    List<BsonDocument> getCommands();

    /** Discards all recorded commands. */
    void clear();
}

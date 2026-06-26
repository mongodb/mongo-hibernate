/*
 * Copyright 2024-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.cfg;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;

class MongoConfigurationTests {

    @Test
    void rejectsBothSettingsAndClient() {
        var settings = MongoClientSettings.builder().build();
        var client = mock(MongoClient.class);
        assertThatThrownBy(() -> new MongoConfiguration(settings, client, "db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void rejectsNeitherSettingsNorClient() {
        assertThatThrownBy(() -> new MongoConfiguration(null, null, "db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exactly one");
    }
}

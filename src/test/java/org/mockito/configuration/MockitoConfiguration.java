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

package org.mockito.configuration;

import org.mockito.Answers;
import org.mockito.stubbing.Answer;

/**
 * Mockito's global configuration overriding mechanism. Before the <a
 * href="https://github.com/mockito/mockito/issues/971">issue</a> is resolved, this seems the best way to configure
 * {@link Answers#RETURNS_SMART_NULLS RETURNS_SMART_NULLS} as the default Mock {@link Answer}.
 *
 * @mongoCme This class does not have to be thread-safe, as per the documentation of {@link IMockitoConfiguration}.
 */
public final class MockitoConfiguration extends DefaultMockitoConfiguration {
    public MockitoConfiguration() {}

    public Answer<Object> getDefaultAnswer() {
        return Answers.RETURNS_SMART_NULLS;
    }
}

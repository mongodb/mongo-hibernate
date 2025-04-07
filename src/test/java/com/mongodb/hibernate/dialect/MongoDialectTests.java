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

package com.mongodb.hibernate.dialect;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoDialectTests {
    @Test
    @SuppressWarnings("deprecation")
    void constructorFailsIfVersionIsNotSupported(@Mock DialectResolutionInfo info) {
        when(info.makeCopyOrDefault(any())).thenCallRealMethod();
        when(info.makeCopy()).thenCallRealMethod();
        when(info.getDatabaseMajorVersion()).thenReturn(5);
        when(info.getDatabaseMinorVersion()).thenReturn(3);
        assertThatThrownBy(() -> new MongoDialect(info))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("The minimum supported version of MongoDB is 6.0, but you are using 5.3");
    }

    @Test
    @SuppressWarnings("deprecation")
    void noArgConstructorFails() {
        assertThrows(RuntimeException.class, MongoDialect::new);
    }
}

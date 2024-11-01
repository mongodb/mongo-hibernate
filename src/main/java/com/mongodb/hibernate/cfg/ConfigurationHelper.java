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

package com.mongodb.hibernate.cfg;

import static org.hibernate.internal.util.NullnessUtil.castNonNull;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Collection of helper methods for dealing with configuration settings. */
public final class ConfigurationHelper {

    private ConfigurationHelper() {}

    public static String getRequiredConfiguration(Map<String, Object> configurationValues, String property) {
        return castNonNull(doGetConfiguration(configurationValues, property, true));
    }

    public static @Nullable String getOptionalConfiguration(Map<String, Object> configurationValues, String property) {
        return doGetConfiguration(configurationValues, property, false);
    }

    private static @Nullable String doGetConfiguration(
            Map<String, Object> configurationValues, String property, boolean required) {
        var configuration = configurationValues.get(property);
        if (configuration == null && required) {
            throw new ConfigurationException(property, "value required");
        }
        if (configuration != null && !(configuration instanceof String)) {
            throw new ConfigurationException(property, "value is not of string type");
        }
        return (String) configuration;
    }
}

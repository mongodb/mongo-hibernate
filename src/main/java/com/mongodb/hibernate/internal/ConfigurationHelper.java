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

import java.util.Map;
import org.hibernate.internal.util.config.ConfigurationException;

/** This class is not part of the public API and may be removed or changed at any time */
public class ConfigurationHelper {

    /**
     * Get the config value as an int
     *
     * @param name The config setting name.
     * @param values The map of config values
     * @param defaultValue The default value to use if not found
     * @return The value.
     */
    public static int getInt(String name, Map<String, Object> values, int defaultValue) {
        Object value = values.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        throw new ConfigurationException("Could not determine how to handle configuration value [name=" + name
                + ", value=" + value + "(" + value.getClass().getName() + ")] as int");
    }
}

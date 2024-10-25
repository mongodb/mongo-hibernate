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

package com.mongodb.hibernate.exception;

public class ConfigurationException extends RuntimeException {

    private final String property;

    public ConfigurationException(String property, String message) {
        super(message);
        this.property = property;
    }

    public ConfigurationException(String property, String message, Throwable cause) {
        super(message, cause);
        this.property = property;
    }

    @Override
    public String getMessage() {
        return String.format("Invalid '%s' configuration: %s", property, super.getMessage());
    }
}

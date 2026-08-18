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

package com.mongodb.hibernate.type;

import java.time.Duration;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * Holds constants referenced by fully-qualified name in HQL, which is how a literal of an arbitrary type is written.
 * Each {@code FIRST_}/{@code THIRD_} pair holds the values of the correspondingly numbered seed item in
 * {@link UnwrappedDomainTypeIntegrationTests}.
 */
public final class UnwrappedDomainTypeConstants {

    public static final Duration FIRST_DURATION = Duration.ofSeconds(90, 500);
    public static final Year FIRST_YEAR = Year.of(2024);
    public static final ZoneId FIRST_ZONE_ID = ZoneId.of("Europe/Paris");
    public static final ZoneOffset FIRST_ZONE_OFFSET = ZoneOffset.of("+02:00");
    public static final TimeZone FIRST_TIME_ZONE = TimeZone.getTimeZone("America/New_York");

    public static final Duration THIRD_DURATION = Duration.ofSeconds(180, 1);
    public static final Year THIRD_YEAR = Year.of(2026);
    public static final ZoneId THIRD_ZONE_ID = ZoneId.of("Asia/Tokyo");
    public static final ZoneOffset THIRD_ZONE_OFFSET = ZoneOffset.of("+09:00");
    public static final TimeZone THIRD_TIME_ZONE = TimeZone.getTimeZone("Asia/Tokyo");

    private UnwrappedDomainTypeConstants() {}
}

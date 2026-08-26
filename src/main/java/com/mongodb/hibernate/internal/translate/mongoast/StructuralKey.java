/*
 * Copyright 2026-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.translate.mongoast;

import java.util.List;

/**
 * What distinguishes one expression's structure from another: a tag naming the kind of expression, and its components
 * in order. A component is either the expression's own data or a child expression.
 *
 * <p>Every component must compare by value rather than identity, since that is what makes two separately built
 * expressions of the same shape compare equal.
 *
 * @param tag names the kind of expression, and must differ between kinds
 * @param fields the components, where omitting one makes this expression indistinguishable from one that differs only
 *     in that component
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record StructuralKey(String tag, List<Object> fields) {}

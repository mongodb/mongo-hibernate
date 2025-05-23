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

package com.mongodb.hibernate.query.select;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity(name = "Book")
@Table(name = "books")
class Book {
    @Id
    int id;

    // TODO-HIBERNATE-48 dummy values are set for currently null value is not supported
    String title = "";
    Boolean outOfStock = false;
    Integer publishYear = 0;
    Long isbn13 = 0L;
    Double discount = 0.0;
    BigDecimal price = new BigDecimal("0.0");

    Book() {}

    Book(int id, String title, Integer publishYear, Boolean outOfStock) {
        this.id = id;
        this.title = title;
        this.publishYear = publishYear;
        this.outOfStock = outOfStock;
    }
}

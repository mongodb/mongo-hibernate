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

package com.mongodb.hibernate.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity(name = "Book")
@Table(name = Book.COLLECTION_NAME)
public class Book {
    public static final String COLLECTION_NAME = "books";

    @Id
    public int id;

    // TODO-HIBERNATE-48 dummy values are set for currently null value is not supported
    public String title = "";
    public Boolean outOfStock = false;
    public Integer publishYear = 0;
    public Long isbn13 = 0L;
    public Double discount = 0.0;
    public BigDecimal price = new BigDecimal("0.0");

    public Book() {}

    public Book(int id, String title, Integer publishYear, Boolean outOfStock) {
        this.id = id;
        this.title = title;
        this.publishYear = publishYear;
        this.outOfStock = outOfStock;
    }
}

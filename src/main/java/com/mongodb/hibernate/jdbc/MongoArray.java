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

package com.mongodb.hibernate.jdbc;

import com.mongodb.hibernate.internal.type.ValueConversions;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public final class MongoArray implements ArrayAdapter {
    private final Object contents;

    private MongoArray(Object contents) {
        this.contents = contents;
    }

    public MongoArray(Object[] contents) {
        this((Object) contents);
    }

    @Override
    public Object getArray() {
        // Hibernate ORM does not call `Connection.getTypeMap`/`setTypeMap`, therefore we are free to ignore it
        return contents;
    }

    public static Builder builder(ValueConversions.Type.ArrayOrCollection contentsType, int size) {
        var type = contentsType.type();
        if (type.isArray()) {
            return new ArrayBuilder(Array.newInstance(contentsType.elementType().type(), size));
        } else {
            // VAKOTODO remove, it's no longer used
            return new CollectionBuilder(new ArrayList<>(size));
        }
    }

    public abstract static class Builder {
        private Builder() {}

        public abstract void add(int i, Object element);

        public abstract MongoArray build();
    }

    @SuppressWarnings("ArrayRecordComponent")
    private static final class ArrayBuilder extends Builder {
        private final Object contents;

        ArrayBuilder(Object contents) {
            this.contents = contents;
        }

        @Override
        public void add(int i, Object element) {
            Array.set(contents, i, element);
        }

        @Override
        public MongoArray build() {
            return new MongoArray(contents);
        }
    }

    private static final class CollectionBuilder extends Builder {
        private final List<Object> contents;

        CollectionBuilder(List<Object> contents) {
            this.contents = contents;
        }

        @Override
        public void add(int i, Object element) {
            contents.add(i, element);
        }

        @Override
        public MongoArray build() {
            return new MongoArray(contents);
        }
    }
}

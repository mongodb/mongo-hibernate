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

package com.mongodb.hibernate.internal.boot;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;

/** Visits all declare fields and classes in a class hierarchy. */
public interface ClassElementChecker {
    ClassElementChecker CURRENT_TIMESTAMP_WITH_DB_SOURCE = new ClassElementChecker() {
        @Override
        public boolean check(Field field) {
            return checkAnnotation(field);
        }

        @Override
        public boolean check(Method method) {
            return checkAnnotation(method);
        }

        boolean checkAnnotation(AnnotatedElement object) {
            final var annotation = object.getAnnotation(CurrentTimestamp.class);
            return annotation != null && annotation.source() == SourceType.DB;
        }

        @Override
        public String toString() {
            return "Annotation @CurrentTimestamp(source=DB) is forbidden";
        }
    };

    /**
     * Visits all methods and fields in the class hierarchy for a particular derived class
     *
     * @param persistentClass the derived class to start visiting
     * @param includeProperties also checks the component properties of the class
     * @param checks the visitor to use
     */
    static void check(PersistentClass persistentClass, boolean includeProperties, ClassElementChecker... checks) {
        check(persistentClass.getMappedClass(), checks);
        if (includeProperties) {
            for (final var property : persistentClass.getProperties()) {
                check(property, checks);
            }
        }
    }

    /**
     * Recursively visits all properties and checks classes on their components
     *
     * @param property the Hibernate property to check
     * @param checks the properties to check
     */
    static void check(Property property, ClassElementChecker... checks) {
        if (property.getValue() instanceof Component component) {
            check(component.getComponentClass(), checks);
            for (final var child : component.getProperties()) {
                check(child, checks);
            }
        }
    }

    /**
     * Visits all methods in the class hierarchy for a particular derived class
     *
     * @param clazz the derived class to start visiting
     * @param checks the checks to apply to this code
     */
    static void check(Class<?> clazz, ClassElementChecker... checks) {
        for (var c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields()) {
                for (final var check : checks) {
                    if (check.check(field)) {
                        throw new FeatureNotSupportedException("Field %s of class %s is not supported: %s"
                                .formatted(
                                        field.getName(),
                                        field.getDeclaringClass().getCanonicalName(),
                                        check));
                    }
                }
            }
            for (var method : c.getDeclaredMethods()) {
                for (final var check : checks) {
                    if (check.check(method)) {
                        throw new FeatureNotSupportedException("Method %s %s(%s) of class %s is not supported: %s"
                                .formatted(
                                        method.getReturnType().getCanonicalName(),
                                        method.getName(),
                                        Stream.of(method.getParameterTypes())
                                                .map(Class::getCanonicalName)
                                                .collect(Collectors.joining(", ")),
                                        method.getDeclaringClass().getCanonicalName(),
                                        check));
                    }
                }
            }
        }
    }

    static ClassElementChecker forbid(Class<? extends Annotation> annotation) {
        return new ClassElementChecker() {
            @Override
            public boolean check(Field field) {
                return field.isAnnotationPresent(annotation);
            }

            @Override
            public boolean check(Method method) {
                return method.isAnnotationPresent(annotation);
            }

            @Override
            public String toString() {
                return "Annotation %s is forbidden".formatted(annotation.getCanonicalName());
            }
        };
    }

    /**
     * Checks a class's field
     *
     * @param field the field definition to check
     * @return true if this element is forbidden
     */
    boolean check(Field field);

    /**
     * Checks a class's method
     *
     * @param method the method definition to check
     * @return true if this element is forbidden
     */
    boolean check(Method method);
}

/*
 * Copyright (C) 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Filter for extensions.
 * Allows filtering by plugin ID, extension class name, and extension type.
 *
 * @author PF4J
 */
public class ExtensionFilter {

    private final List<Predicate<ExtensionWrapper<?>>> predicates = new ArrayList<>();

    private ExtensionFilter() {
        // Private constructor to enforce builder pattern
    }

    /**
     * Create a new builder for ExtensionFilter.
     *
     * @return a new ExtensionFilter builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Check if an extension matches this filter.
     *
     * @param extension the extension to check
     * @return true if the extension matches the filter
     */
    public boolean matches(ExtensionWrapper<?> extension) {
        return predicates.stream().allMatch(predicate -> predicate.test(extension));
    }

    /**
     * Apply this filter to a list of extensions.
     *
     * @param extensions the list of extensions to filter
     * @param <T> the extension type
     * @return a new list containing only extensions that match the filter
     */
    @SuppressWarnings("unchecked")
    public <T> List<ExtensionWrapper<T>> apply(List<ExtensionWrapper<T>> extensions) {
        List<ExtensionWrapper<T>> result = new ArrayList<>();
        for (ExtensionWrapper<T> extension : extensions) {
            if (matches(extension)) {
                result.add(extension);
            }
        }
        return result;
    }

    /**
     * Builder for ExtensionFilter.
     */
    public static class Builder {

        private final ExtensionFilter filter = new ExtensionFilter();

        /**
         * Filter by plugin ID.
         *
         * @param pluginId the plugin ID to match
         * @return this builder
         */
        public Builder pluginId(String pluginId) {
            Objects.requireNonNull(pluginId, "Plugin ID cannot be null");
            filter.predicates.add(extension -> pluginId.equals(extension.getDescriptor().pluginId));
            return this;
        }

        /**
         * Filter by plugin IDs.
         *
         * @param pluginIds the plugin IDs to match
         * @return this builder
         */
        public Builder pluginIds(String... pluginIds) {
            List<String> pluginIdList = Arrays.asList(pluginIds);
            filter.predicates.add(extension -> pluginIdList.contains(extension.getDescriptor().pluginId));
            return this;
        }

        /**
         * Filter by extension class name.
         *
         * @param className the class name to match
         * @return this builder
         */
        public Builder className(String className) {
            Objects.requireNonNull(className, "Class name cannot be null");
            filter.predicates.add(extension -> className.equals(extension.getDescriptor().extensionClass.getName()));
            return this;
        }

        /**
         * Filter by extension class names.
         *
         * @param classNames the class names to match
         * @return this builder
         */
        public Builder classNames(String... classNames) {
            List<String> classNameList = Arrays.asList(classNames);
            filter.predicates.add(extension -> classNameList.contains(extension.getDescriptor().extensionClass.getName()));
            return this;
        }

        /**
         * Filter by extension type.
         *
         * @param type the extension type to match
         * @return this builder
         */
        public Builder type(Class<?> type) {
            Objects.requireNonNull(type, "Type cannot be null");
            filter.predicates.add(extension -> type.isAssignableFrom(extension.getDescriptor().extensionClass));
            return this;
        }

        /**
         * Add a custom predicate.
         *
         * @param predicate the predicate to add
         * @return this builder
         */
        public Builder predicate(Predicate<ExtensionWrapper<?>> predicate) {
            Objects.requireNonNull(predicate, "Predicate cannot be null");
            filter.predicates.add(predicate);
            return this;
        }

        /**
         * Build the ExtensionFilter.
         *
         * @return the built ExtensionFilter
         */
        public ExtensionFilter build() {
            return filter;
        }

    }

}

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

import java.util.Objects;

public final class ExtensionFilter {

    private static final ExtensionFilter EMPTY = new ExtensionFilter(null, null, null);

    private final String pluginId;
    private final String extensionClassName;
    private final Class<?> extensionType;

    private ExtensionFilter(String pluginId, String extensionClassName, Class<?> extensionType) {
        this.pluginId = pluginId;
        this.extensionClassName = extensionClassName;
        this.extensionType = extensionType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ExtensionFilter empty() {
        return EMPTY;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getExtensionClassName() {
        return extensionClassName;
    }

    public Class<?> getExtensionType() {
        return extensionType;
    }

    public boolean isEmpty() {
        return pluginId == null && extensionClassName == null && extensionType == null;
    }

    public boolean hasExtensionType() {
        return extensionType != null;
    }

    public boolean matches(String candidatePluginId) {
        return pluginId == null || Objects.equals(pluginId, candidatePluginId);
    }

    public boolean matches(String candidatePluginId, String candidateExtensionClassName) {
        return matches(candidatePluginId)
            && (extensionClassName == null || Objects.equals(extensionClassName, candidateExtensionClassName));
    }

    public boolean matches(String candidatePluginId, String candidateExtensionClassName, Class<?> candidateExtensionClass) {
        return matches(candidatePluginId, candidateExtensionClassName)
            && (extensionType == null || extensionType.isAssignableFrom(candidateExtensionClass));
    }

    public boolean matches(PluginManager pluginManager, Class<?> extensionClass) {
        PluginWrapper plugin = pluginManager.whichPlugin(extensionClass);
        String candidatePluginId = plugin != null ? plugin.getPluginId() : null;
        return matches(candidatePluginId, extensionClass.getName(), extensionClass);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExtensionFilter)) {
            return false;
        }
        ExtensionFilter that = (ExtensionFilter) o;
        return Objects.equals(pluginId, that.pluginId)
            && Objects.equals(extensionClassName, that.extensionClassName)
            && Objects.equals(extensionType, that.extensionType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginId, extensionClassName, extensionType);
    }

    @Override
    public String toString() {
        return "ExtensionFilter{"
            + "pluginId='" + pluginId + '\''
            + ", extensionClassName='" + extensionClassName + '\''
            + ", extensionType=" + extensionType
            + '}';
    }

    public static final class Builder {

        private String pluginId;
        private String extensionClassName;
        private Class<?> extensionType;

        private Builder() {
        }

        public Builder pluginId(String pluginId) {
            this.pluginId = pluginId;
            return this;
        }

        public Builder extensionClassName(String extensionClassName) {
            this.extensionClassName = extensionClassName;
            return this;
        }

        public Builder extensionType(Class<?> extensionType) {
            this.extensionType = extensionType;
            return this;
        }

        public ExtensionFilter build() {
            if (pluginId == null && extensionClassName == null && extensionType == null) {
                return ExtensionFilter.empty();
            }
            return new ExtensionFilter(pluginId, extensionClassName, extensionType);
        }

    }

}

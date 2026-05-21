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

import java.util.function.Predicate;

public interface ExtensionFilter extends Predicate<ExtensionFilter.ExtensionInfo> {

    static ExtensionFilter acceptingAll() {
        return info -> true;
    }

    static ExtensionFilter pluginId(String pluginId) {
        return info -> pluginId.equals(info.pluginId());
    }

    static ExtensionFilter className(String className) {
        return info -> className.equals(info.className());
    }

    static ExtensionFilter classNamePattern(String regex) {
        return info -> info.className() != null && info.className().matches(regex);
    }

    static ExtensionFilter implementationClass(Class<?> implementationClass) {
        return info -> implementationClass.getName().equals(info.className());
    }

    static ExtensionFilter of(String pluginId, String className, Class<?> implementationClass) {
        return new ExtensionFilter() {
            @Override
            public boolean test(ExtensionInfo info) {
                if (pluginId != null && !pluginId.equals(info.pluginId())) {
                    return false;
                }
                if (className != null && !className.equals(info.className())) {
                    return false;
                }
                if (implementationClass != null && !implementationClass.getName().equals(info.className())) {
                    return false;
                }
                return true;
            }
        };
    }

    record ExtensionInfo(String pluginId, String className, Class<?> extensionClass) {
        public static ExtensionInfo of(String pluginId, String className) {
            return new ExtensionInfo(pluginId, className, null);
        }
    }

}
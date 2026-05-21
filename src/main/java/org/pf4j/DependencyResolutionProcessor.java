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

/**
 * Resolves plugin dependencies after a plugin has been loaded.
 * This processor calls {@link AbstractPluginManager#resolvePlugins()}
 * to check and resolve cross-plugin dependencies.
 *
 * @author Decebal Suiu
 */
public class DependencyResolutionProcessor implements PluginLoadingProcessor {

    @Override
    public void process(AbstractPluginManager pluginManager, PluginLoadingContext context) {
        pluginManager.resolvePlugins();
    }

}
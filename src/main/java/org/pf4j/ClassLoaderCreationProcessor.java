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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor that creates the plugin class loader for loading plugin classes.
 *
 * @author Decebal Suiu
 */
public class ClassLoaderCreationProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(ClassLoaderCreationProcessor.class);

    @Override
    public PluginLoadingContext process(PluginLoadingContext context) {
        AbstractPluginManager pluginManager = context.getPluginManager();
        PluginDescriptor pluginDescriptor = context.getPluginDescriptor();

        log.debug("Loading plugin '{}'", context.getResolvedPath());
        ClassLoader pluginClassLoader = pluginManager.getPluginLoader().loadPlugin(context.getResolvedPath(), pluginDescriptor);
        log.debug("Loaded plugin '{}' with class loader '{}'", context.getResolvedPath(), pluginClassLoader);

        return context.withPluginClassLoader(pluginClassLoader);
    }

}

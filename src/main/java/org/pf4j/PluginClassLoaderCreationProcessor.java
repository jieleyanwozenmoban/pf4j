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

import java.nio.file.Path;

/**
 * Creates the plugin class loader for a plugin.
 *
 * @author Decebal Suiu
 */
public class PluginClassLoaderCreationProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(PluginClassLoaderCreationProcessor.class);

    @Override
    public void process(AbstractPluginManager pluginManager, PluginLoadingContext context) {
        Path pluginPath = context.getPluginPath();
        PluginDescriptor pluginDescriptor = context.getPluginDescriptor();

        log.debug("Loading plugin '{}'", pluginPath);
        ClassLoader pluginClassLoader = pluginManager.getPluginLoader().loadPlugin(pluginPath, pluginDescriptor);
        log.debug("Loaded plugin '{}' with class loader '{}'", pluginPath, pluginClassLoader);

        context.setPluginClassLoader(pluginClassLoader);
    }

}
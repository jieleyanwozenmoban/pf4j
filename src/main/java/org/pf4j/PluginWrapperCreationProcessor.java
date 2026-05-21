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
 * Creates the {@link PluginWrapper}, checks whether the plugin is disabled or
 * invalid, and registers the plugin in the plugin manager's internal structures.
 *
 * @author Decebal Suiu
 */
public class PluginWrapperCreationProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(PluginWrapperCreationProcessor.class);

    @Override
    public void process(AbstractPluginManager pluginManager, PluginLoadingContext context) {
        Path pluginPath = context.getPluginPath();
        PluginDescriptor pluginDescriptor = context.getPluginDescriptor();
        ClassLoader pluginClassLoader = context.getPluginClassLoader();

        PluginWrapper pluginWrapper = pluginManager.createPluginWrapper(pluginDescriptor, pluginPath, pluginClassLoader);

        if (pluginManager.isPluginDisabled(pluginDescriptor.getPluginId())) {
            log.info("Plugin '{}' is disabled", pluginPath);
            pluginWrapper.setPluginState(PluginState.DISABLED);
        }

        if (!pluginManager.isPluginValid(pluginWrapper)) {
            log.warn("Plugin '{}' is invalid and it will be disabled", pluginPath);
            pluginWrapper.setPluginState(PluginState.DISABLED);
            pluginWrapper.setFailedException(new PluginRuntimeException("Plugin validation failed"));
        }

        log.debug("Created wrapper '{}' for plugin '{}'", pluginWrapper, pluginPath);

        String pluginId = pluginDescriptor.getPluginId();

        pluginManager.addPlugin(pluginWrapper);
        pluginManager.getUnresolvedPlugins().add(pluginWrapper);
        pluginManager.getPluginClassLoaders().put(pluginId, pluginClassLoader);

        context.setPluginWrapper(pluginWrapper);
    }

}
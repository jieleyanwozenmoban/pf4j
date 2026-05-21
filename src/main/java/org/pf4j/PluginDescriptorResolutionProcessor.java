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
 * Resolves the plugin descriptor from the plugin path.
 * Validates the descriptor and checks for duplicate plugin paths and ids.
 *
 * @author Decebal Suiu
 */
public class PluginDescriptorResolutionProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(PluginDescriptorResolutionProcessor.class);

    @Override
    public void process(AbstractPluginManager pluginManager, PluginLoadingContext context) {
        Path pluginPath = context.getPluginPath();

        String pluginId = pluginManager.idForPath(pluginPath);
        if (pluginId != null) {
            throw new PluginAlreadyLoadedException(pluginId, pluginPath);
        }

        PluginDescriptorFinder pluginDescriptorFinder = pluginManager.getPluginDescriptorFinder();
        log.debug("Use '{}' to find plugins descriptors", pluginDescriptorFinder);
        log.debug("Finding plugin descriptor for plugin '{}'", pluginPath);
        PluginDescriptor pluginDescriptor = pluginDescriptorFinder.find(pluginPath);
        pluginManager.validatePluginDescriptor(pluginDescriptor);

        pluginId = pluginDescriptor.getPluginId();
        if (pluginManager.plugins.containsKey(pluginId)) {
            PluginWrapper loadedPlugin = pluginManager.getPlugin(pluginId);
            throw new PluginRuntimeException("There is an already loaded plugin ({}) "
                    + "with the same id ({}) as the plugin at path '{}'. Simultaneous loading "
                    + "of plugins with the same PluginId is not currently supported.\n"
                    + "As a workaround you may include PluginVersion and PluginProvider "
                    + "in PluginId.",
                loadedPlugin, pluginId, pluginPath);
        }

        log.debug("Found descriptor {}", pluginDescriptor);
        String pluginClassName = pluginDescriptor.getPluginClass();
        log.debug("Class '{}' for plugin '{}'",  pluginClassName, pluginPath);

        context.setPluginDescriptor(pluginDescriptor);
    }

}
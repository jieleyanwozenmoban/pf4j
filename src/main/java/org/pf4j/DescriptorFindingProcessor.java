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
 * Processor that finds and validates the plugin descriptor from the plugin path.
 *
 * @author Decebal Suiu
 */
public class DescriptorFindingProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(DescriptorFindingProcessor.class);

    @Override
    public PluginLoadingContext process(PluginLoadingContext context) {
        AbstractPluginManager pluginManager = context.getPluginManager();
        PluginDescriptorFinder pluginDescriptorFinder = pluginManager.getPluginDescriptorFinder();

        log.debug("Use '{}' to find plugins descriptors", pluginDescriptorFinder);
        log.debug("Finding plugin descriptor for plugin '{}'", context.getResolvedPath());

        PluginDescriptor pluginDescriptor = pluginDescriptorFinder.find(context.getResolvedPath());
        pluginManager.validatePluginDescriptor(pluginDescriptor);

        String pluginId = pluginDescriptor.getPluginId();
        if (pluginManager.plugins.containsKey(pluginId)) {
            PluginWrapper loadedPlugin = pluginManager.getPlugin(pluginId);
            throw new PluginRuntimeException(
                "There is an already loaded plugin ({}) "
                    + "with the same id ({}) as the plugin at path '{}'. Simultaneous loading "
                    + "of plugins with the same PluginId is not currently supported.\n"
                    + "As a workaround you may include PluginVersion and PluginProvider "
                    + "in PluginId.",
                loadedPlugin, pluginId, context.getResolvedPath());
        }

        log.debug("Found descriptor {}", pluginDescriptor);

        return context.withPluginDescriptor(pluginDescriptor);
    }

}

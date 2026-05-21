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
 * Processor that registers the plugin in the plugin manager by adding it to
 * the plugins map, unresolved plugins list, and class loaders map.
 *
 * @author Decebal Suiu
 */
public class PluginRegistrationProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistrationProcessor.class);

    @Override
    public PluginLoadingContext process(PluginLoadingContext context) {
        AbstractPluginManager pluginManager = context.getPluginManager();
        PluginWrapper pluginWrapper = context.getPluginWrapper();
        String pluginId = context.getPluginDescriptor().getPluginId();

        pluginManager.addPlugin(pluginWrapper);
        pluginManager.getUnresolvedPlugins().add(pluginWrapper);
        pluginManager.getPluginClassLoaders().put(pluginId, context.getPluginClassLoader());

        log.debug("Registered plugin '{}' in plugin manager", pluginId);

        return context;
    }

}

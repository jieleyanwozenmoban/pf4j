package org.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DescriptorParsingProcessor implements PluginLoadingProcessor {
    private static final Logger log = LoggerFactory.getLogger(DescriptorParsingProcessor.class);

    @Override
    public void process(PluginLoadingContext context, AbstractPluginManager pluginManager) {
        PluginDescriptorFinder pluginDescriptorFinder = pluginManager.getPluginDescriptorFinder();
        log.debug("Use '{}' to find plugins descriptors", pluginDescriptorFinder);
        log.debug("Finding plugin descriptor for plugin '{}'", context.getPluginPath());
        PluginDescriptor pluginDescriptor = pluginDescriptorFinder.find(context.getPluginPath());
        pluginManager.validatePluginDescriptor(pluginDescriptor);

        String pluginId = pluginDescriptor.getPluginId();
        if (pluginManager.getPlugin(pluginId) != null) {
            PluginWrapper loadedPlugin = pluginManager.getPlugin(pluginId);
            throw new PluginRuntimeException("There is an already loaded plugin ({}) "
                    + "with the same id ({}) as the plugin at path '{}'. Simultaneous loading "
                    + "of plugins with the same PluginId is not currently supported.\n"
                    + "As a workaround you may include PluginVersion and PluginProvider "
                    + "in PluginId.",
                loadedPlugin, pluginId, context.getPluginPath());
        }

        log.debug("Found descriptor {}", pluginDescriptor);
        context.setPluginDescriptor(pluginDescriptor);
    }
}

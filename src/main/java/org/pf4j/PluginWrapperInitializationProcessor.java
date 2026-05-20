package org.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginWrapperInitializationProcessor implements PluginLoadingProcessor {
    private static final Logger log = LoggerFactory.getLogger(PluginWrapperInitializationProcessor.class);

    @Override
    public void process(PluginLoadingContext context, AbstractPluginManager pluginManager) {
        PluginDescriptor pluginDescriptor = context.getPluginDescriptor();

        PluginWrapper pluginWrapper = pluginManager.createPluginWrapper(
            pluginDescriptor,
            context.getPluginPath(),
            context.getPluginClassLoader()
        );

        if (pluginManager.isPluginDisabled(pluginDescriptor.getPluginId())) {
            log.info("Plugin '{}' is disabled", context.getPluginPath());
            pluginWrapper.setPluginState(PluginState.DISABLED);
        }

        if (!pluginManager.isPluginValid(pluginWrapper)) {
            log.warn("Plugin '{}' is invalid and it will be disabled", context.getPluginPath());
            pluginWrapper.setPluginState(PluginState.DISABLED);
            pluginWrapper.setFailedException(new PluginRuntimeException("Plugin validation failed"));
        }

        log.debug("Created wrapper '{}' for plugin '{}'", pluginWrapper, context.getPluginPath());

        String pluginId = pluginDescriptor.getPluginId();

        pluginManager.addPlugin(pluginWrapper);
        pluginManager.getUnresolvedPlugins().add(pluginWrapper);

        pluginManager.getPluginClassLoaders().put(pluginId, context.getPluginClassLoader());

        context.setPluginWrapper(pluginWrapper);
    }
}

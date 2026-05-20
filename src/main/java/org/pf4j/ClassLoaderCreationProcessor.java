package org.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassLoaderCreationProcessor implements PluginLoadingProcessor {
    private static final Logger log = LoggerFactory.getLogger(ClassLoaderCreationProcessor.class);

    @Override
    public void process(PluginLoadingContext context, AbstractPluginManager pluginManager) {
        PluginDescriptor pluginDescriptor = context.getPluginDescriptor();
        String pluginClassName = pluginDescriptor.getPluginClass();
        log.debug("Class '{}' for plugin '{}'",  pluginClassName, context.getPluginPath());

        log.debug("Loading plugin '{}'", context.getPluginPath());
        ClassLoader pluginClassLoader = pluginManager.getPluginLoader().loadPlugin(context.getPluginPath(), pluginDescriptor);
        log.debug("Loaded plugin '{}' with class loader '{}'", context.getPluginPath(), pluginClassLoader);

        context.setPluginClassLoader(pluginClassLoader);
    }
}

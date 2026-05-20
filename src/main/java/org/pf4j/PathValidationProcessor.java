package org.pf4j;

public class PathValidationProcessor implements PluginLoadingProcessor {
    @Override
    public void process(PluginLoadingContext context, AbstractPluginManager pluginManager) {
        String pluginId = pluginManager.idForPath(context.getPluginPath());
        if (pluginId != null) {
            throw new PluginAlreadyLoadedException(pluginId, context.getPluginPath());
        }
    }
}

package org.pf4j;

public interface PluginLoadingProcessor {
    void process(PluginLoadingContext context, AbstractPluginManager pluginManager);
}

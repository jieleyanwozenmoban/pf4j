package org.pf4j;

/**
 * Plugin factory for creating plugin instances
 */
public interface PluginFactory {
    Plugin create(PluginWrapper pluginWrapper);
}

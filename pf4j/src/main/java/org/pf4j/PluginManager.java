package org.pf4j;

import java.nio.file.Path;
import java.util.List;

/**
 * Plugin Manager interface
 */
public interface PluginManager {

    /**
     * Load a plugin from the specified path
     */
    String loadPlugin(Path pluginPath);

    /**
     * Start a plugin by its ID
     */
    PluginState startPlugin(String pluginId);

    /**
     * Stop a plugin by its ID
     */
    PluginState stopPlugin(String pluginId);

    /**
     * Unload a plugin by its ID
     */
    boolean unloadPlugin(String pluginId);

    /**
     * Delete a plugin by its ID
     */
    boolean deletePlugin(String pluginId);

    /**
     * Disable a plugin by its ID
     */
    void disablePlugin(String pluginId);

    /**
     * Enable a plugin by its ID
     */
    void enablePlugin(String pluginId);

    /**
     * Get the plugin wrapper for a plugin ID
     */
    PluginWrapper getPlugin(String pluginId);

    /**
     * Get all plugins
     */
    List<PluginWrapper> getPlugins();

    /**
     * Get all started plugins
     */
    List<PluginWrapper> getStartedPlugins();

    /**
     * Get the plugin state
     */
    PluginState getPluginState(String pluginId);

    /**
     * Load all plugins from the plugins directory
     */
    void loadPlugins();

    /**
     * Start all plugins
     */
    void startPlugins();

    /**
     * Stop all plugins
     */
    void stopPlugins();

    /**
     * Unload all plugins
     */
    void unloadPlugins();

    /**
     * Resolve dependencies
     */
    void resolveDependencies();

    /**
     * Get the dependency resolver
     */
    DependencyResolver getDependencyResolver();

    /**
     * Get the system version
     */
    String getSystemVersion();
}

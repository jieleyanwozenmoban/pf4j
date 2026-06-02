package org.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Abstract implementation of the PluginManager interface.
 * This class provides the core plugin management functionality including
 * lifecycle management and dependency resolution.
 */
public abstract class AbstractPluginManager implements PluginManager {

    private static final Logger log = LoggerFactory.getLogger(AbstractPluginManager.class);

    protected List<PluginWrapper> plugins = new ArrayList<>();
    protected Map<String, PluginState> pluginStates = new HashMap<>();
    protected DependencyResolver dependencyResolver;
    protected Path pluginsRoot;
    protected PluginFactory pluginFactory;
    protected String systemVersion;

    public AbstractPluginManager() {
        this.dependencyResolver = new DependencyResolver();
        this.pluginFactory = createPluginFactory();
    }

    public AbstractPluginManager(Path pluginsRoot) {
        this();
        this.pluginsRoot = pluginsRoot;
    }

    @Override
    public String loadPlugin(Path pluginPath) {
        log.debug("Loading plugin from: {}", pluginPath);

        try {
            PluginDescriptor pluginDescriptor = getPluginDescriptorFinder().find(pluginPath);
            String pluginId = pluginDescriptor.getPluginId();

            if (getPlugin(pluginId) != null) {
                log.warn("Plugin '{}' already loaded", pluginId);
                return pluginId;
            }

            ClassLoader pluginClassLoader = createPluginClassLoader(pluginPath);
            PluginWrapper pluginWrapper = createPluginWrapper(pluginDescriptor, pluginPath, pluginClassLoader);

            plugins.add(pluginWrapper);
            dependencyResolver.addPluginDescriptor(pluginDescriptor);

            log.info("Loaded plugin '{}' from '{}'", pluginId, pluginPath);
            return pluginId;

        } catch (PluginException e) {
            log.error("Failed to load plugin from '{}': {}", pluginPath, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public PluginState startPlugin(String pluginId) {
        log.debug("Starting plugin '{}'", pluginId);

        PluginWrapper pluginWrapper = getPlugin(pluginId);
        if (pluginWrapper == null) {
            throw new PluginRuntimeException("Plugin '{}' not found", pluginId);
        }

        PluginState pluginState = pluginWrapper.getPluginState();
        if (pluginState == PluginState.STARTED) {
            log.debug("Plugin '{}' already started", pluginId);
            return pluginState;
        }

        // Check dependencies before starting
        List<String> dependencies = dependencyResolver.getDependencies(pluginId);
        for (String depId : dependencies) {
            PluginWrapper depPlugin = getPlugin(depId);
            if (depPlugin == null || depPlugin.getPluginState() != PluginState.STARTED) {
                throw new PluginRuntimeException(
                    "Cannot start plugin '{}': dependency '{}' is not started", pluginId, depId);
            }
        }

        try {
            Plugin plugin = pluginWrapper.getPluginFactory().create(pluginWrapper);
            plugin.start();

            pluginWrapper.setPluginState(PluginState.STARTED);
            pluginStates.put(pluginId, PluginState.STARTED);

            log.info("Started plugin '{}'", pluginId);
            return PluginState.STARTED;

        } catch (Exception e) {
            log.error("Failed to start plugin '{}': {}", pluginId, e.getMessage(), e);
            pluginWrapper.setPluginState(PluginState.STOPPED);
            return PluginState.STOPPED;
        }
    }

    @Override
    public PluginState stopPlugin(String pluginId) {
        log.debug("Stopping plugin '{}'", pluginId);

        PluginWrapper pluginWrapper = getPlugin(pluginId);
        if (pluginWrapper == null) {
            throw new PluginRuntimeException("Plugin '{}' not found", pluginId);
        }

        PluginState pluginState = pluginWrapper.getPluginState();
        if (pluginState != PluginState.STARTED) {
            log.debug("Plugin '{}' is not started, current state: {}", pluginId, pluginState);
            return pluginState;
        }

        // Check if other plugins depend on this one
        List<String> dependents = dependencyResolver.getDependents(pluginId);
        for (String dependentId : dependents) {
            PluginWrapper dependentPlugin = getPlugin(dependentId);
            if (dependentPlugin != null && dependentPlugin.getPluginState() == PluginState.STARTED) {
                log.warn("Plugin '{}' is still started and depends on '{}', stopping dependent first",
                        dependentId, pluginId);
                stopPlugin(dependentId);
            }
        }

        try {
            Plugin plugin = pluginWrapper.getPluginFactory().create(pluginWrapper);
            plugin.stop();

            pluginWrapper.setPluginState(PluginState.STOPPED);
            pluginStates.put(pluginId, PluginState.STOPPED);

            log.info("Stopped plugin '{}'", pluginId);
            return PluginState.STOPPED;

        } catch (Exception e) {
            log.error("Failed to stop plugin '{}': {}", pluginId, e.getMessage(), e);
            return pluginWrapper.getPluginState();
        }
    }

    /**
     * Unload a plugin by its ID.
     *
     * This method includes dependency protection: before unloading a plugin,
     * it checks if any other loaded plugins depend on it. If dependencies exist,
     * the unload operation is rejected with a clear log message.
     *
     * @param pluginId the ID of the plugin to unload
     * @return true if the plugin was successfully unloaded, false otherwise
     */
    @Override
    public boolean unloadPlugin(String pluginId) {
        return unloadPlugin(pluginId, true);
    }

    /**
     * Unload a plugin by its ID with optional dependency check.
     *
     * @param pluginId the ID of the plugin to unload
     * @param checkDependencies whether to check for dependencies before unloading
     * @return true if the plugin was successfully unloaded, false otherwise
     */
    public boolean unloadPlugin(String pluginId, boolean checkDependencies) {
        log.debug("Unloading plugin '{}'", pluginId);

        PluginWrapper pluginWrapper = getPlugin(pluginId);
        if (pluginWrapper == null) {
            log.warn("Plugin '{}' not found, cannot unload", pluginId);
            return false;
        }

        // Dependency protection: check if other plugins depend on this one
        if (checkDependencies) {
            List<String> dependents = dependencyResolver.getDependents(pluginId);

            if (!dependents.isEmpty()) {
                // Find which dependents are still loaded
                List<String> loadedDependents = new ArrayList<>();
                for (String dependentId : dependents) {
                    if (getPlugin(dependentId) != null) {
                        loadedDependents.add(dependentId);
                    }
                }

                if (!loadedDependents.isEmpty()) {
                    log.error("Cannot unload plugin '{}': it is required by other plugins: {}",
                            pluginId, loadedDependents);

                    // Also get transitive dependents for more detailed logging
                    List<String> transitiveDependents = dependencyResolver.getTransitiveDependents(pluginId);
                    if (transitiveDependents.size() > loadedDependents.size()) {
                        log.error("Transitive dependents of plugin '{}' include: {}",
                                pluginId, transitiveDependents);
                    }

                    return false;
                }
            }
        }

        // Stop the plugin if it's running
        if (pluginWrapper.getPluginState() == PluginState.STARTED) {
            log.debug("Plugin '{}' is started, stopping it before unload", pluginId);
            stopPlugin(pluginId);
        }

        try {
            // Remove from plugins list
            plugins.remove(pluginWrapper);

            // Remove from dependency resolver
            // Note: In a full implementation, we would rebuild the dependency graph
            // For now, we rely on the resolver's internal state

            // Clean up classloader
            ClassLoader classLoader = pluginWrapper.getPluginClassLoader();
            if (classLoader instanceof PluginClassLoader) {
                try {
                    ((PluginClassLoader) classLoader).close();
                } catch (IOException e) {
                    log.warn("Failed to close classloader for plugin '{}': {}", pluginId, e.getMessage());
                }
            }

            pluginWrapper.setPluginState(PluginState.DELETED);
            pluginStates.remove(pluginId);

            log.info("Unloaded plugin '{}'", pluginId);
            return true;

        } catch (Exception e) {
            log.error("Failed to unload plugin '{}': {}", pluginId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean deletePlugin(String pluginId) {
        log.debug("Deleting plugin '{}'", pluginId);

        // First unload the plugin
        if (!unloadPlugin(pluginId)) {
            log.warn("Cannot delete plugin '{}': unload failed", pluginId);
            return false;
        }

        // Delete plugin files
        PluginWrapper pluginWrapper = getPlugin(pluginId);
        if (pluginWrapper != null) {
            Path pluginPath = pluginWrapper.getPluginPath();
            try {
                deleteRecursive(pluginPath);
                log.info("Deleted plugin files from '{}'", pluginPath);
                return true;
            } catch (IOException e) {
                log.error("Failed to delete plugin files '{}': {}", pluginPath, e.getMessage(), e);
                return false;
            }
        }

        return false;
    }

    @Override
    public void disablePlugin(String pluginId) {
        log.debug("Disabling plugin '{}'", pluginId);
        // Implementation would persist disabled state
        pluginStates.put(pluginId, PluginState.DISABLED);
    }

    @Override
    public void enablePlugin(String pluginId) {
        log.debug("Enabling plugin '{}'", pluginId);
        // Implementation would clear disabled state
    }

    @Override
    public PluginWrapper getPlugin(String pluginId) {
        for (PluginWrapper plugin : plugins) {
            if (plugin.getPluginId().equals(pluginId)) {
                return plugin;
            }
        }
        return null;
    }

    @Override
    public List<PluginWrapper> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    @Override
    public List<PluginWrapper> getStartedPlugins() {
        List<PluginWrapper> started = new ArrayList<>();
        for (PluginWrapper plugin : plugins) {
            if (plugin.getPluginState() == PluginState.STARTED) {
                started.add(plugin);
            }
        }
        return started;
    }

    @Override
    public PluginState getPluginState(String pluginId) {
        return pluginStates.get(pluginId);
    }

    @Override
    public void loadPlugins() {
        if (pluginsRoot == null || !Files.exists(pluginsRoot)) {
            log.warn("Plugins root '{}' does not exist", pluginsRoot);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsRoot)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    loadPlugin(path);
                }
            }
        } catch (IOException e) {
            log.error("Failed to load plugins from '{}': {}", pluginsRoot, e.getMessage(), e);
        }
    }

    @Override
    public void startPlugins() {
        for (PluginWrapper plugin : plugins) {
            try {
                startPlugin(plugin.getPluginId());
            } catch (Exception e) {
                log.error("Failed to start plugin '{}': {}", plugin.getPluginId(), e.getMessage());
            }
        }
    }

    @Override
    public void stopPlugins() {
        // Stop in reverse order to handle dependencies
        List<PluginWrapper> reversed = new ArrayList<>(plugins);
        Collections.reverse(reversed);

        for (PluginWrapper plugin : reversed) {
            try {
                stopPlugin(plugin.getPluginId());
            } catch (Exception e) {
                log.error("Failed to stop plugin '{}': {}", plugin.getPluginId(), e.getMessage());
            }
        }
    }

    @Override
    public void unloadPlugins() {
        List<PluginWrapper> pluginsCopy = new ArrayList<>(plugins);
        for (PluginWrapper plugin : pluginsCopy) {
            unloadPlugin(plugin.getPluginId());
        }
    }

    @Override
    public void resolveDependencies() {
        dependencyResolver.resolve();
        log.debug("Resolved dependencies:\n{}", dependencyResolver.getDependenciesGraph());
    }

    @Override
    public DependencyResolver getDependencyResolver() {
        return dependencyResolver;
    }

    @Override
    public String getSystemVersion() {
        return systemVersion;
    }

    // Abstract methods to be implemented by subclasses
    protected abstract PluginDescriptorFinder getPluginDescriptorFinder();

    protected abstract ClassLoader createPluginClassLoader(Path pluginPath) throws PluginException;

    protected abstract PluginFactory createPluginFactory();

    protected abstract PluginWrapper createPluginWrapper(PluginDescriptor descriptor,
                                                         Path pluginPath,
                                                         ClassLoader classLoader);

    protected void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    public void setSystemVersion(String systemVersion) {
        this.systemVersion = systemVersion;
    }
}

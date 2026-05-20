package org.pf4j;

import java.nio.file.Path;

public class PluginLoadingContext {

    private final DefaultPluginManager pluginManager;
    private final Path originalPluginPath;
    private Path pluginPath;
    private String pluginId;
    private PluginDescriptor pluginDescriptor;
    private ClassLoader pluginClassLoader;
    private PluginWrapper pluginWrapper;

    public PluginLoadingContext(DefaultPluginManager pluginManager, Path pluginPath) {
        this.pluginManager = pluginManager;
        this.originalPluginPath = pluginPath;
        this.pluginPath = pluginPath;
    }

    public DefaultPluginManager getPluginManager() {
        return pluginManager;
    }

    public Path getOriginalPluginPath() {
        return originalPluginPath;
    }

    public Path getPluginPath() {
        return pluginPath;
    }

    public void setPluginPath(Path pluginPath) {
        this.pluginPath = pluginPath;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public PluginDescriptor getPluginDescriptor() {
        return pluginDescriptor;
    }

    public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
        this.pluginDescriptor = pluginDescriptor;
    }

    public ClassLoader getPluginClassLoader() {
        return pluginClassLoader;
    }

    public void setPluginClassLoader(ClassLoader pluginClassLoader) {
        this.pluginClassLoader = pluginClassLoader;
    }

    public PluginWrapper getPluginWrapper() {
        return pluginWrapper;
    }

    public void setPluginWrapper(PluginWrapper pluginWrapper) {
        this.pluginWrapper = pluginWrapper;
    }

}

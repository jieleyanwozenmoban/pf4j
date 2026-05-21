package org.pf4j;

import java.nio.file.Path;

/**
 * Context object used during plugin loading.
 */
public class PluginLoadingContext {

    private Path pluginPath;
    private PluginDescriptor pluginDescriptor;
    private ClassLoader pluginClassLoader;
    private PluginWrapper pluginWrapper;
    private String pluginId;
    private boolean aborted;

    public PluginLoadingContext(Path pluginPath) {
        this.pluginPath = pluginPath;
    }

    public boolean isAborted() {
        return aborted;
    }

    public void setAborted(boolean aborted) {
        this.aborted = aborted;
    }

    public Path getPluginPath() {
        return pluginPath;
    }

    public void setPluginPath(Path pluginPath) {
        this.pluginPath = pluginPath;
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

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }
}

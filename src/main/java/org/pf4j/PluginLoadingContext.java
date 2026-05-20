package org.pf4j;

import java.nio.file.Path;

public class PluginLoadingContext {
    private Path pluginPath;
    private PluginDescriptor pluginDescriptor;
    private ClassLoader pluginClassLoader;
    private PluginWrapper pluginWrapper;

    public PluginLoadingContext(Path pluginPath) {
        this.pluginPath = pluginPath;
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
}

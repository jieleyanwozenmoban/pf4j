package org.pf4j;

/**
 * Plugin wrapper that holds plugin information and state
 */
public class PluginWrapper {
    private final PluginManager pluginManager;
    private final PluginDescriptor pluginDescriptor;
    private final Path pluginPath;
    private final ClassLoader pluginClassLoader;
    private PluginFactory pluginFactory;
    private PluginState pluginState;
    private final RuntimeMode runtimeMode;

    public PluginWrapper(PluginManager pluginManager, PluginDescriptor pluginDescriptor,
                         Path pluginPath, ClassLoader pluginClassLoader, RuntimeMode runtimeMode) {
        this.pluginManager = pluginManager;
        this.pluginDescriptor = pluginDescriptor;
        this.pluginPath = pluginPath;
        this.pluginClassLoader = pluginClassLoader;
        this.runtimeMode = runtimeMode;
        this.pluginState = PluginState.CREATED;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public PluginDescriptor getDescriptor() {
        return pluginDescriptor;
    }

    public Path getPluginPath() {
        return pluginPath;
    }

    public ClassLoader getPluginClassLoader() {
        return pluginClassLoader;
    }

    public PluginFactory getPluginFactory() {
        return pluginFactory;
    }

    public void setPluginFactory(PluginFactory pluginFactory) {
        this.pluginFactory = pluginFactory;
    }

    public PluginState getPluginState() {
        return pluginState;
    }

    public void setPluginState(PluginState pluginState) {
        this.pluginState = pluginState;
    }

    public RuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    public String getPluginId() {
        return pluginDescriptor.getPluginId();
    }

    @Override
    public String toString() {
        return "PluginWrapper [descriptor=" + pluginDescriptor + ", pluginPath=" + pluginPath +
               ", pluginState=" + pluginState + ", pluginClassLoader=" + pluginClassLoader + "]";
    }
}

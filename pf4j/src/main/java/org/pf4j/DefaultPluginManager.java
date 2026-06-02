package org.pf4j;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of PluginManager
 */
public class DefaultPluginManager extends AbstractPluginManager {

    private final Map<String, PluginDescriptor> descriptors = new HashMap<>();
    private final Map<String, Plugin> pluginInstances = new HashMap<>();

    public DefaultPluginManager() {
        super();
    }

    public DefaultPluginManager(Path pluginsRoot) {
        super(pluginsRoot);
    }

    @Override
    protected PluginDescriptorFinder getPluginDescriptorFinder() {
        return pluginPath -> descriptors.get(pluginPath.toString());
    }

    @Override
    protected ClassLoader createPluginClassLoader(Path pluginPath) throws PluginException {
        return new PluginClassLoader(new java.net.URL[0], getClass().getClassLoader());
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return pluginWrapper -> {
            String pluginId = pluginWrapper.getPluginId();
            return pluginInstances.computeIfAbsent(pluginId, id -> new BasePlugin());
        };
    }

    @Override
    protected PluginWrapper createPluginWrapper(PluginDescriptor descriptor,
                                                 Path pluginPath,
                                                 ClassLoader classLoader) {
        return new PluginWrapper(this, descriptor, pluginPath, classLoader, RuntimeMode.DEPLOYMENT);
    }

    /**
     * Register a plugin descriptor for testing purposes
     */
    public void registerPluginDescriptor(PluginDescriptor descriptor, Path pluginPath) {
        descriptors.put(pluginPath.toString(), descriptor);
    }

    /**
     * Simple base plugin implementation
     */
    private static class BasePlugin implements Plugin {
        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void delete() {
        }
    }
}

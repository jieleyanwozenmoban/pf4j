package org.pf4j;

import java.nio.file.Files;
import java.nio.file.Path;

import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ValidatePluginPathProcessor implements PluginLoadingProcessor {

    @Override
    public boolean process(PluginLoadingContext context) {
        Path pluginPath = context.getPluginPath();
        if ((pluginPath == null) || Files.notExists(pluginPath)) {
            throw new IllegalArgumentException(String.format("Specified plugin %s does not exist!", pluginPath));
        }

        return true;
    }

}

final class ExpandPluginArchiveProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(ExpandPluginArchiveProcessor.class);

    @Override
    public boolean process(PluginLoadingContext context) {
        Path pluginPath = context.getPluginPath();
        try {
            context.setPluginPath(FileUtils.expandIfZip(pluginPath));
            return true;
        } catch (Exception e) {
            log.warn("Failed to unzip " + pluginPath, e);
            return false;
        }
    }

}

final class ValidatePluginPathUniquenessProcessor implements PluginLoadingProcessor {

    @Override
    public boolean process(PluginLoadingContext context) {
        DefaultPluginManager pluginManager = context.getPluginManager();
        Path pluginPath = context.getPluginPath();
        String pluginId = pluginManager.idForPath(pluginPath);
        if (pluginId != null) {
            throw new PluginAlreadyLoadedException(pluginId, pluginPath);
        }

        return true;
    }

}

final class ParsePluginDescriptorProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(ParsePluginDescriptorProcessor.class);

    @Override
    public boolean process(PluginLoadingContext context) {
        DefaultPluginManager pluginManager = context.getPluginManager();
        Path pluginPath = context.getPluginPath();
        PluginDescriptorFinder pluginDescriptorFinder = pluginManager.getPluginDescriptorFinder();

        log.debug("Use '{}' to find plugins descriptors", pluginDescriptorFinder);
        log.debug("Finding plugin descriptor for plugin '{}'", pluginPath);

        PluginDescriptor pluginDescriptor = pluginDescriptorFinder.find(pluginPath);
        pluginManager.validatePluginDescriptor(pluginDescriptor);

        log.debug("Found descriptor {}", pluginDescriptor);
        log.debug("Class '{}' for plugin '{}'", pluginDescriptor.getPluginClass(), pluginPath);

        context.setPluginDescriptor(pluginDescriptor);
        context.setPluginId(pluginDescriptor.getPluginId());
        return true;
    }

}

final class ValidatePluginIdUniquenessProcessor implements PluginLoadingProcessor {

    @Override
    public boolean process(PluginLoadingContext context) {
        DefaultPluginManager pluginManager = context.getPluginManager();
        String pluginId = context.getPluginId();

        if (pluginManager.plugins.containsKey(pluginId)) {
            PluginWrapper loadedPlugin = pluginManager.getPlugin(pluginId);
            throw new PluginRuntimeException("There is an already loaded plugin ({}) "
                + "with the same id ({}) as the plugin at path '{}'. Simultaneous loading "
                + "of plugins with the same PluginId is not currently supported.\n"
                + "As a workaround you may include PluginVersion and PluginProvider "
                + "in PluginId.",
                loadedPlugin, pluginId, context.getPluginPath());
        }

        return true;
    }

}

final class CreatePluginClassLoaderProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(CreatePluginClassLoaderProcessor.class);

    @Override
    public boolean process(PluginLoadingContext context) {
        DefaultPluginManager pluginManager = context.getPluginManager();
        Path pluginPath = context.getPluginPath();
        PluginDescriptor pluginDescriptor = context.getPluginDescriptor();

        log.debug("Loading plugin '{}'", pluginPath);
        ClassLoader pluginClassLoader = pluginManager.getPluginLoader().loadPlugin(pluginPath, pluginDescriptor);
        log.debug("Loaded plugin '{}' with class loader '{}'", pluginPath, pluginClassLoader);

        context.setPluginClassLoader(pluginClassLoader);
        return true;
    }

}

final class InitializePluginWrapperProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(InitializePluginWrapperProcessor.class);

    @Override
    public boolean process(PluginLoadingContext context) {
        DefaultPluginManager pluginManager = context.getPluginManager();
        PluginDescriptor pluginDescriptor = context.getPluginDescriptor();
        Path pluginPath = context.getPluginPath();
        ClassLoader pluginClassLoader = context.getPluginClassLoader();

        PluginWrapper pluginWrapper = pluginManager.createPluginWrapper(pluginDescriptor, pluginPath, pluginClassLoader);

        if (pluginManager.isPluginDisabled(pluginDescriptor.getPluginId())) {
            log.info("Plugin '{}' is disabled", pluginPath);
            pluginWrapper.setPluginState(PluginState.DISABLED);
        }

        if (!pluginManager.isPluginValid(pluginWrapper)) {
            log.warn("Plugin '{}' is invalid and it will be disabled", pluginPath);
            pluginWrapper.setPluginState(PluginState.DISABLED);
            pluginWrapper.setFailedException(new PluginRuntimeException("Plugin validation failed"));
        }

        log.debug("Created wrapper '{}' for plugin '{}'", pluginWrapper, pluginPath);

        pluginManager.addPlugin(pluginWrapper);
        pluginManager.getUnresolvedPlugins().add(pluginWrapper);
        pluginManager.getPluginClassLoaders().put(pluginDescriptor.getPluginId(), pluginClassLoader);

        context.setPluginWrapper(pluginWrapper);
        return true;
    }

}

final class ResolvePluginDependenciesProcessor implements PluginLoadingProcessor {

    @Override
    public boolean process(PluginLoadingContext context) {
        context.getPluginManager().resolvePlugins();
        return true;
    }

}

/*
 * Copyright (C) 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pf4j;

import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Default implementation of the {@link PluginManager} interface.
 * In essence, it is a {@link ZipPluginManager} plus a {@link JarPluginManager}.
 * So, it can load plugins from jar and zip, simultaneous.
 *
 * <p>This class is not thread-safe.
 *
 * @author Decebal Suiu
 */
public class DefaultPluginManager extends AbstractPluginManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginManager.class);

    public static final String PLUGINS_DIR_CONFIG_PROPERTY_NAME = "pf4j.pluginsConfigDir";

    private List<PluginLoadingProcessor> loadPluginProcessors;
    private List<PluginLoadingProcessor> loadPluginFromPathProcessors;

    public DefaultPluginManager() {
        super();
    }

    public DefaultPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
    }

    public DefaultPluginManager(List<Path> pluginsRoots) {
        super(pluginsRoots);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new CompoundPluginDescriptorFinder()
            .add(new PropertiesPluginDescriptorFinder())
            .add(new ManifestPluginDescriptorFinder());
    }

    @Override
    protected ExtensionFinder createExtensionFinder() {
        DefaultExtensionFinder extensionFinder = new DefaultExtensionFinder(this);
        addPluginStateListener(extensionFinder);

        return extensionFinder;
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new DefaultPluginFactory();
    }

    @Override
    protected ExtensionFactory createExtensionFactory() {
        return new DefaultExtensionFactory();
    }

    @Override
    protected PluginStatusProvider createPluginStatusProvider() {
        String configDir = System.getProperty(PLUGINS_DIR_CONFIG_PROPERTY_NAME);
        Path configPath = configDir != null
            ? Paths.get(configDir)
            : getPluginsRoots().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No pluginsRoot configured"));

        return new DefaultPluginStatusProvider(configPath);
    }

    @Override
    protected PluginRepository createPluginRepository() {
        return new CompoundPluginRepository()
            .add(new DevelopmentPluginRepository(getPluginsRoots()), this::isDevelopment)
            .add(new JarPluginRepository(getPluginsRoots()), this::isNotDevelopment)
            .add(new DefaultPluginRepository(getPluginsRoots()), this::isNotDevelopment);
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return new CompoundPluginLoader()
            .add(new DevelopmentPluginLoader(this), this::isDevelopment)
            .add(new JarPluginLoader(this), this::isNotDevelopment)
            .add(new DefaultPluginLoader(this), this::isNotDevelopment);
    }

    @Override
    protected VersionManager createVersionManager() {
        return new DefaultVersionManager();
    }

    @Override
    protected void initialize() {
        super.initialize();

        if (isDevelopment()) {
            addPluginStateListener(new LoggingPluginStateListener());
        }

        log.info("PF4J version {} in '{}' mode", getVersion(), getRuntimeMode());

        this.loadPluginProcessors = Arrays.asList(
            new PathValidationProcessor(),
            new ArchiveExtractionProcessor(),
            new DescriptorParsingProcessor(),
            new ClassLoaderCreationProcessor(),
            new PluginWrapperInitializationProcessor(),
            new DependencyCheckProcessor()
        );

        this.loadPluginFromPathProcessors = Arrays.asList(
            new ArchiveExtractionProcessor(),
            new DescriptorParsingProcessor(),
            new ClassLoaderCreationProcessor(),
            new PluginWrapperInitializationProcessor()
        );
    }

    @Override
    public String loadPlugin(Path pluginPath) {
        PluginLoadingContext context = new PluginLoadingContext(pluginPath);
        for (PluginLoadingProcessor processor : loadPluginProcessors) {
            processor.process(context);
            if (context.isAborted()) {
                break;
            }
        }
        
        if (context.isAborted()) {
            // If aborted, simulate the behavior of calling resolvePlugins anyway and throwing NPE on descriptor access
            resolvePlugins();
            return context.getPluginWrapper().getDescriptor().getPluginId();
        }

        return context.getPluginId();
    }

    /**
     * Load a plugin from disk. If the path is a zip file, first unpack.
     *
     * @param pluginPath plugin location on disk
     * @return PluginWrapper for the loaded plugin or null if not loaded
     * @throws PluginRuntimeException if problems during load
     */
    @Override
    protected PluginWrapper loadPluginFromPath(Path pluginPath) {
        PluginLoadingContext context = new PluginLoadingContext(pluginPath);
        for (PluginLoadingProcessor processor : loadPluginFromPathProcessors) {
            processor.process(context);
            if (context.isAborted()) {
                break;
            }
        }
        return context.getPluginWrapper();
    }

    protected class PathValidationProcessor implements PluginLoadingProcessor {
        @Override
        public void process(PluginLoadingContext context) {
            Path pluginPath = context.getPluginPath();
            if ((pluginPath == null) || Files.notExists(pluginPath)) {
                throw new IllegalArgumentException(String.format("Specified plugin %s does not exist!", pluginPath));
            }
            log.debug("Loading plugin from '{}'", pluginPath);
        }
    }

    protected class ArchiveExtractionProcessor implements PluginLoadingProcessor {
        @Override
        public void process(PluginLoadingContext context) {
            try {
                context.setPluginPath(FileUtils.expandIfZip(context.getPluginPath()));
            } catch (Exception e) {
                log.warn("Failed to unzip " + context.getPluginPath(), e);
                context.setAborted(true);
            }
        }
    }

    protected class DescriptorParsingProcessor implements PluginLoadingProcessor {
        @Override
        public void process(PluginLoadingContext context) {
            Path pluginPath = context.getPluginPath();
            String pluginId = idForPath(pluginPath);
            if (pluginId != null) {
                throw new PluginAlreadyLoadedException(pluginId, pluginPath);
            }

            PluginDescriptorFinder pluginDescriptorFinder = getPluginDescriptorFinder();
            log.debug("Use '{}' to find plugins descriptors", pluginDescriptorFinder);
            log.debug("Finding plugin descriptor for plugin '{}'", pluginPath);
            PluginDescriptor pluginDescriptor = pluginDescriptorFinder.find(pluginPath);
            validatePluginDescriptor(pluginDescriptor);

            pluginId = pluginDescriptor.getPluginId();
            if (plugins.containsKey(pluginId)) {
                PluginWrapper loadedPlugin = getPlugin(pluginId);
                throw new PluginRuntimeException("There is an already loaded plugin ({}) "
                        + "with the same id ({}) as the plugin at path '{}'. Simultaneous loading "
                        + "of plugins with the same PluginId is not currently supported.\n"
                        + "As a workaround you may include PluginVersion and PluginProvider "
                        + "in PluginId.",
                    loadedPlugin, pluginId, pluginPath);
            }

            log.debug("Found descriptor {}", pluginDescriptor);
            context.setPluginDescriptor(pluginDescriptor);
            context.setPluginId(pluginId);
        }
    }

    protected class ClassLoaderCreationProcessor implements PluginLoadingProcessor {
        @Override
        public void process(PluginLoadingContext context) {
            PluginDescriptor pluginDescriptor = context.getPluginDescriptor();
            Path pluginPath = context.getPluginPath();
            String pluginClassName = pluginDescriptor.getPluginClass();
            log.debug("Class '{}' for plugin '{}'",  pluginClassName, pluginPath);

            log.debug("Loading plugin '{}'", pluginPath);
            ClassLoader pluginClassLoader = getPluginLoader().loadPlugin(pluginPath, pluginDescriptor);
            log.debug("Loaded plugin '{}' with class loader '{}'", pluginPath, pluginClassLoader);

            context.setPluginClassLoader(pluginClassLoader);
        }
    }

    protected class PluginWrapperInitializationProcessor implements PluginLoadingProcessor {
        @Override
        public void process(PluginLoadingContext context) {
            PluginDescriptor pluginDescriptor = context.getPluginDescriptor();
            Path pluginPath = context.getPluginPath();
            ClassLoader pluginClassLoader = context.getPluginClassLoader();

            PluginWrapper pluginWrapper = createPluginWrapper(pluginDescriptor, pluginPath, pluginClassLoader);

            if (isPluginDisabled(pluginDescriptor.getPluginId())) {
                log.info("Plugin '{}' is disabled", pluginPath);
                pluginWrapper.setPluginState(PluginState.DISABLED);
            }

            if (!isPluginValid(pluginWrapper)) {
                log.warn("Plugin '{}' is invalid and it will be disabled", pluginPath);
                pluginWrapper.setPluginState(PluginState.DISABLED);
                pluginWrapper.setFailedException(new PluginRuntimeException("Plugin validation failed"));
            }

            log.debug("Created wrapper '{}' for plugin '{}'", pluginWrapper, pluginPath);

            addPlugin(pluginWrapper);
            getUnresolvedPlugins().add(pluginWrapper);
            getPluginClassLoaders().put(pluginDescriptor.getPluginId(), pluginClassLoader);

            context.setPluginWrapper(pluginWrapper);
        }
    }

    protected class DependencyCheckProcessor implements PluginLoadingProcessor {
        @Override
        public void process(PluginLoadingContext context) {
            resolvePlugins();
        }
    }

}

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
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
    }

    private List<PluginLoadingProcessor> pluginLoadingProcessors;

    /**
     * Create the list of {@link PluginLoadingProcessor}s to be used for single-plugin loading.
     * <p>
     * The default chain includes path validation, archive expansion, descriptor resolution,
     * class loader creation, plugin wrapper creation, and dependency resolution.
     * <p>
     * Override this method to customize the loading pipeline.
     *
     * @return an ordered list of processors
     */
    protected List<PluginLoadingProcessor> createPluginLoadingProcessors() {
        List<PluginLoadingProcessor> processors = new ArrayList<>();
        processors.add(new ArchiveExpansionProcessor());
        processors.add(new PluginDescriptorResolutionProcessor());
        processors.add(new PluginClassLoaderCreationProcessor());
        processors.add(new PluginWrapperCreationProcessor());
        processors.add(new DependencyResolutionProcessor());
        return processors;
    }

    /**
     * Returns the plugin loading processor chain, creating it lazily if needed.
     * The returned list is unmodifiable.
     */
    protected List<PluginLoadingProcessor> getPluginLoadingProcessors() {
        if (pluginLoadingProcessors == null) {
            pluginLoadingProcessors = Collections.unmodifiableList(createPluginLoadingProcessors());
        }
        return pluginLoadingProcessors;
    }

    /**
     * Load a plugin from disk using the processor chain.
     * The path is validated, zip files are expanded, the descriptor is resolved,
     * a class loader is created, the plugin wrapper is initialized, and
     * dependencies are resolved.
     *
     * @param pluginPath plugin location on disk
     * @return the pluginId of the loaded plugin
     * @throws IllegalArgumentException if the path is null or does not exist
     * @throws PluginRuntimeException if problems during load
     */
    @Override
    public String loadPlugin(Path pluginPath) {
        if ((pluginPath == null) || Files.notExists(pluginPath)) {
            throw new IllegalArgumentException(String.format("Specified plugin %s does not exist!", pluginPath));
        }

        log.debug("Loading plugin from '{}'", pluginPath);

        PluginLoadingContext context = new PluginLoadingContext();
        context.setPluginPath(pluginPath);

        for (PluginLoadingProcessor processor : getPluginLoadingProcessors()) {
            processor.process(this, context);
        }

        return context.getPluginWrapper().getDescriptor().getPluginId();
    }

    /**
     * Load a plugin from path without resolving dependencies.
     * The dependency resolution step is skipped here because
     * {@link #loadPlugins()} calls {@code resolvePlugins()} once after loading all plugins.
     *
     * @param pluginPath plugin location on disk
     * @return PluginWrapper for the loaded plugin
     * @throws PluginRuntimeException if problems during load
     */
    @Override
    protected PluginWrapper loadPluginFromPath(Path pluginPath) {
        PluginLoadingContext context = new PluginLoadingContext();
        context.setPluginPath(pluginPath);

        for (PluginLoadingProcessor processor : getPluginLoadingProcessors()) {
            if (processor instanceof DependencyResolutionProcessor) {
                continue;
            }
            processor.process(this, context);
        }

        return context.getPluginWrapper();
    }

}

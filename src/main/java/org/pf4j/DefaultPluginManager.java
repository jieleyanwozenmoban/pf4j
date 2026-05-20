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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private List<PluginLoadingProcessor> pluginLoadingProcessors;
    private List<PluginLoadingProcessor> loadPluginProcessors;

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
    public String loadPlugin(Path pluginPath) {
        log.debug("Loading plugin from '{}'", pluginPath);

        PluginLoadingContext context = new PluginLoadingContext(this, pluginPath);
        executeProcessors(getLoadPluginProcessors(), context);

        return context.getPluginWrapper().getDescriptor().getPluginId();
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

    @Override
    protected PluginWrapper loadPluginFromPath(Path pluginPath) {
        PluginLoadingContext context = new PluginLoadingContext(this, pluginPath);
        executeProcessors(getPluginLoadingProcessors(), context);

        return context.getPluginWrapper();
    }

    protected List<PluginLoadingProcessor> createPluginLoadingProcessors() {
        return Arrays.asList(
            new ExpandPluginArchiveProcessor(),
            new ValidatePluginPathUniquenessProcessor(),
            new ParsePluginDescriptorProcessor(),
            new ValidatePluginIdUniquenessProcessor(),
            new CreatePluginClassLoaderProcessor(),
            new InitializePluginWrapperProcessor()
        );
    }

    protected List<PluginLoadingProcessor> createLoadPluginProcessors() {
        List<PluginLoadingProcessor> processors = new ArrayList<>();
        processors.add(new ValidatePluginPathProcessor());
        processors.addAll(getPluginLoadingProcessors());
        processors.add(new ResolvePluginDependenciesProcessor());

        return processors;
    }

    protected List<PluginLoadingProcessor> getPluginLoadingProcessors() {
        if (pluginLoadingProcessors == null) {
            pluginLoadingProcessors = Collections.unmodifiableList(new ArrayList<>(createPluginLoadingProcessors()));
        }

        return pluginLoadingProcessors;
    }

    protected List<PluginLoadingProcessor> getLoadPluginProcessors() {
        if (loadPluginProcessors == null) {
            loadPluginProcessors = Collections.unmodifiableList(new ArrayList<>(createLoadPluginProcessors()));
        }

        return loadPluginProcessors;
    }

    protected void executeProcessors(List<PluginLoadingProcessor> processors, PluginLoadingContext context) {
        for (PluginLoadingProcessor processor : processors) {
            if (!processor.process(context)) {
                return;
            }
        }
    }

}

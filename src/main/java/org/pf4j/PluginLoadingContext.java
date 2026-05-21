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

/**
 * Immutable context object that carries information through the plugin loading pipeline.
 * Each processor may return a new context with additional information populated.
 *
 * @author Decebal Suiu
 */
public final class PluginLoadingContext {

    private final Path originalPath;
    private final Path resolvedPath;
    private final PluginDescriptor pluginDescriptor;
    private final ClassLoader pluginClassLoader;
    private final PluginWrapper pluginWrapper;
    private final AbstractPluginManager pluginManager;

    private PluginLoadingContext(
            AbstractPluginManager pluginManager,
            Path originalPath,
            Path resolvedPath,
            PluginDescriptor pluginDescriptor,
            ClassLoader pluginClassLoader,
            PluginWrapper pluginWrapper) {
        this.pluginManager = pluginManager;
        this.originalPath = originalPath;
        this.resolvedPath = resolvedPath;
        this.pluginDescriptor = pluginDescriptor;
        this.pluginClassLoader = pluginClassLoader;
        this.pluginWrapper = pluginWrapper;
    }

    public static PluginLoadingContext create(AbstractPluginManager pluginManager, Path pluginPath) {
        return new PluginLoadingContext(pluginManager, pluginPath, pluginPath, null, null, null);
    }

    public AbstractPluginManager getPluginManager() {
        return pluginManager;
    }

    public Path getOriginalPath() {
        return originalPath;
    }

    public Path getResolvedPath() {
        return resolvedPath;
    }

    public PluginLoadingContext withResolvedPath(Path resolvedPath) {
        return new PluginLoadingContext(pluginManager, originalPath, resolvedPath, pluginDescriptor, pluginClassLoader, pluginWrapper);
    }

    public PluginDescriptor getPluginDescriptor() {
        return pluginDescriptor;
    }

    public PluginLoadingContext withPluginDescriptor(PluginDescriptor pluginDescriptor) {
        return new PluginLoadingContext(pluginManager, originalPath, resolvedPath, pluginDescriptor, pluginClassLoader, pluginWrapper);
    }

    public ClassLoader getPluginClassLoader() {
        return pluginClassLoader;
    }

    public PluginLoadingContext withPluginClassLoader(ClassLoader pluginClassLoader) {
        return new PluginLoadingContext(pluginManager, originalPath, resolvedPath, pluginDescriptor, pluginClassLoader, pluginWrapper);
    }

    public PluginWrapper getPluginWrapper() {
        return pluginWrapper;
    }

    public PluginLoadingContext withPluginWrapper(PluginWrapper pluginWrapper) {
        return new PluginLoadingContext(pluginManager, originalPath, resolvedPath, pluginDescriptor, pluginClassLoader, pluginWrapper);
    }

}

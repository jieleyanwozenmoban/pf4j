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
 * Context object passed through the {@link PluginLoadingProcessor} chain.
 * Holds the intermediate state of a plugin loading operation.
 *
 * @author Decebal Suiu
 */
public class PluginLoadingContext {

    private Path pluginPath;
    private PluginDescriptor pluginDescriptor;
    private ClassLoader pluginClassLoader;
    private PluginWrapper pluginWrapper;

    public PluginLoadingContext() {
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
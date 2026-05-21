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

/**
 * Expands a zip plugin archive into a directory before loading.
 * If the path is not a zip file, it passes through unchanged.
 *
 * @author Decebal Suiu
 */
public class ArchiveExpansionProcessor implements PluginLoadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(ArchiveExpansionProcessor.class);

    @Override
    public void process(AbstractPluginManager pluginManager, PluginLoadingContext context) {
        Path pluginPath = context.getPluginPath();

        try {
            pluginPath = FileUtils.expandIfZip(pluginPath);
        } catch (Exception e) {
            log.warn("Failed to unzip " + pluginPath, e);
            throw new PluginRuntimeException(e, "Failed to unzip " + pluginPath);
        }

        context.setPluginPath(pluginPath);
    }

}
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

/**
 * A processor in the plugin loading pipeline. Each implementation is responsible
 * for a single phase of the plugin loading process.
 * <p>
 * Processors are composed into a chain and invoked sequentially by
 * {@link DefaultPluginManager}.
 *
 * @author Decebal Suiu
 */
@FunctionalInterface
public interface PluginLoadingProcessor {

    /**
     * Execute this processor's loading phase.
     *
     * @param pluginManager the plugin manager instance
     * @param context the loading context carrying intermediate state
     * @throws PluginRuntimeException if processing fails
     */
    void process(AbstractPluginManager pluginManager, PluginLoadingContext context);

}
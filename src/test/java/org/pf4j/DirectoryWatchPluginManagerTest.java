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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.test.PluginJar;
import org.pf4j.test.TestExtension;
import org.pf4j.test.TestExtensionPoint;
import org.pf4j.test.TestPlugin;
import org.pf4j.test.Version1Extension;
import org.pf4j.test.Version2Extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryWatchPluginManagerTest {

    @TempDir
    Path pluginsPath;

    private DirectoryWatchPluginManager pluginManager;

    @BeforeEach
    public void setUp() {
        pluginManager = new DirectoryWatchPluginManager(pluginsPath);
        pluginManager.setWatchEventDelay(100);
    }

    @AfterEach
    public void tearDown() {
        if (pluginManager != null) {
            pluginManager.stopWatching();
            pluginManager.unloadPlugins();
        }
    }

    @Test
    public void addPlugin() throws Exception {
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        pluginManager.startWatching();

        assertEquals(0, pluginManager.getPlugins().size());
        assertEquals(0, pluginManager.getExtensions(TestExtensionPoint.class).size());

        new PluginJar.Builder(pluginsPath.resolve("test-plugin.jar"), "test-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("1.0.0")
            .extension(TestExtension.class.getName())
            .build();

        assertTrue(awaitCondition(() -> pluginManager.getPlugins().size() == 1, 5000),
            "Plugin should be loaded after adding jar file");

        List<TestExtensionPoint> extensions = pluginManager.getExtensions(TestExtensionPoint.class);
        assertEquals(1, extensions.size());
        assertEquals(new TestExtension().saySomething(), extensions.get(0).saySomething());
    }

    @Test
    public void deletePlugin() throws Exception {
        PluginJar pluginJar = new PluginJar.Builder(pluginsPath.resolve("test-plugin.jar"), "test-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("1.0.0")
            .extension(TestExtension.class.getName())
            .build();

        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        assertEquals(1, pluginManager.getPlugins().size());
        assertEquals(1, pluginManager.getExtensions(TestExtensionPoint.class).size());

        pluginManager.startWatching();

        Files.delete(pluginJar.path());

        assertTrue(awaitCondition(() -> pluginManager.getPlugins().isEmpty(), 5000),
            "Plugin should be unloaded after deleting jar file");

        assertEquals(0, pluginManager.getExtensions(TestExtensionPoint.class).size());
    }

    @Test
    public void upgradePlugin() throws Exception {
        PluginJar pluginJarV1 = new PluginJar.Builder(pluginsPath.resolve("test-plugin.jar"), "test-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("1.0.0")
            .extension(Version1Extension.class.getName())
            .build();

        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        assertEquals(1, pluginManager.getPlugins().size());
        assertEquals("1.0.0", pluginManager.getPlugin("test-plugin").getDescriptor().getVersion());
        List<TestExtensionPoint> extensionsV1 = pluginManager.getExtensions(TestExtensionPoint.class);
        assertEquals(1, extensionsV1.size());
        assertEquals("version1", extensionsV1.get(0).saySomething());

        pluginManager.startWatching();

        Files.delete(pluginJarV1.path());

        assertTrue(awaitCondition(() -> pluginManager.getPlugins().isEmpty(), 5000),
            "Plugin v1 should be unloaded after deleting jar file");

        new PluginJar.Builder(pluginsPath.resolve("test-plugin.jar"), "test-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("2.0.0")
            .extension(Version2Extension.class.getName())
            .build();

        assertTrue(awaitCondition(() -> pluginManager.getPlugins().size() == 1, 5000),
            "Plugin v2 should be loaded after adding new jar file");

        assertEquals("2.0.0", pluginManager.getPlugin("test-plugin").getDescriptor().getVersion());
        List<TestExtensionPoint> extensionsV2 = pluginManager.getExtensions(TestExtensionPoint.class);
        assertEquals(1, extensionsV2.size());
        assertEquals("version2", extensionsV2.get(0).saySomething());
    }

    private boolean awaitCondition(java.util.function.Supplier<Boolean> condition, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition.get()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

}

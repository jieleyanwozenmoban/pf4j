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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryWatchPluginManagerTest {

    private DirectoryWatchPluginManager pluginManager;

    @TempDir
    Path pluginsPath;

    @BeforeEach
    public void setUp() throws Exception {
        pluginManager = new DirectoryWatchPluginManager(pluginsPath);
    }

    @AfterEach
    public void tearDown() {
        if (pluginManager != null) {
            pluginManager.stopWatching();
            pluginManager.unloadPlugins();
        }
    }

    @Test
    public void testAddPlugin() throws Exception {
        pluginManager.startWatching();

        assertEquals(0, pluginManager.getExtensions(TestExtensionPoint.class).size());

        PluginJar pluginJar = new PluginJar.Builder(pluginsPath.resolve("test-plugin-1.0.0.jar"), "test-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("1.0.0")
            .extension(TestExtension.class.getName())
            .build();

        waitForExtensions(1);

        List<TestExtensionPoint> extensions = pluginManager.getExtensions(TestExtensionPoint.class);
        assertEquals(1, extensions.size());
        assertEquals("I am a test extension", extensions.get(0).saySomething());

        PluginWrapper plugin = pluginManager.getPlugin("test-plugin");
        assertNotNull(plugin);
        assertTrue(plugin.getPluginState().isStarted());
    }

    @Test
    public void testDeletePlugin() throws Exception {
        PluginJar pluginJar = new PluginJar.Builder(pluginsPath.resolve("test-plugin-1.0.0.jar"), "test-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("1.0.0")
            .extension(TestExtension.class.getName())
            .build();

        pluginManager.startWatching();

        waitForExtensions(1);
        assertEquals(1, pluginManager.getExtensions(TestExtensionPoint.class).size());

        Files.delete(pluginJar.path());

        waitForExtensions(0);

        assertEquals(0, pluginManager.getExtensions(TestExtensionPoint.class).size());
        assertEquals(0, pluginManager.getPlugins().size());
    }

    @Test
    public void testUpgradePlugin() throws Exception {
        PluginJar pluginJarV1 = new PluginJar.Builder(pluginsPath.resolve("test-plugin-1.0.0.jar"), "test-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("1.0.0")
            .extension(TestExtension.class.getName())
            .build();

        pluginManager.startWatching();

        waitForExtensions(1);
        assertEquals(1, pluginManager.getExtensions(TestExtensionPoint.class).size());

        PluginWrapper pluginV1 = pluginManager.getPlugin("test-plugin");
        assertNotNull(pluginV1);
        assertEquals("1.0.0", pluginV1.getDescriptor().getVersion());

        Files.delete(pluginJarV1.path());

        PluginJar pluginJarV2 = new PluginJar.Builder(pluginsPath.resolve("test-plugin-2.0.0.jar"), "test-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("2.0.0")
            .extension(TestExtension.class.getName())
            .build();

        waitForExtensions(1);

        List<TestExtensionPoint> extensions = pluginManager.getExtensions(TestExtensionPoint.class);
        assertEquals(1, extensions.size());
        assertEquals("I am a test extension", extensions.get(0).saySomething());

        PluginWrapper pluginV2 = pluginManager.getPlugin("test-plugin");
        assertNotNull(pluginV2);
        assertEquals("2.0.0", pluginV2.getDescriptor().getVersion());
        assertTrue(pluginV2.getPluginState().isStarted());
    }

    @Test
    public void testAddAndDeleteMultiplePlugins() throws Exception {
        pluginManager.startWatching();

        assertEquals(0, pluginManager.getExtensions(TestExtensionPoint.class).size());

        PluginJar pluginJar1 = new PluginJar.Builder(pluginsPath.resolve("plugin-one-1.0.0.jar"), "plugin-one")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("1.0.0")
            .extension(TestExtension.class.getName())
            .build();

        PluginJar pluginJar2 = new PluginJar.Builder(pluginsPath.resolve("plugin-two-1.0.0.jar"), "plugin-two")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion("1.0.0")
            .extension(TestExtension.class.getName())
            .build();

        waitForExtensions(2);
        assertEquals(2, pluginManager.getExtensions(TestExtensionPoint.class).size());

        Files.delete(pluginJar1.path());

        waitForExtensions(1);
        assertEquals(1, pluginManager.getExtensions(TestExtensionPoint.class).size());
        assertNotNull(pluginManager.getPlugin("plugin-two"));

        Files.delete(pluginJar2.path());

        waitForExtensions(0);
        assertEquals(0, pluginManager.getExtensions(TestExtensionPoint.class).size());
        assertEquals(0, pluginManager.getPlugins().size());
    }

    private void waitForExtensions(int expectedCount) throws InterruptedException {
        long timeout = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < timeout) {
            if (pluginManager.getExtensions(TestExtensionPoint.class).size() == expectedCount) {
                return;
            }
            Thread.sleep(200);
        }
        int actualCount = pluginManager.getExtensions(TestExtensionPoint.class).size();
        assertEquals(expectedCount, actualCount,
            "Timed out waiting for extensions count to reach " + expectedCount
                + ", actual count is " + actualCount);
    }
}
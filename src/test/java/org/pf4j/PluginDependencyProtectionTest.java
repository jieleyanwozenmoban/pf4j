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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.test.PluginZip;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class to verify the plugin dependency protection mechanism.
 *
 * @author Test
 */
public class PluginDependencyProtectionTest {

    private DefaultPluginManager pluginManager;

    @TempDir
    Path pluginsPath;

    @BeforeEach
    public void setUp() {
        pluginManager = new DefaultPluginManager(pluginsPath);
    }

    @Test
    public void testUnloadWithoutDependencies() throws Exception {
        // Create plugin A without dependencies
        PluginZip pluginA = new PluginZip.Builder(pluginsPath.resolve("A-plugin-1.2.3.zip"), "plugin.a")
            .pluginVersion("1.2.3").build();

        pluginManager.loadPlugins();
        assertEquals(1, pluginManager.getPlugins().size());

        pluginManager.startPlugins();
        assertEquals(1, pluginManager.getStartedPlugins().size());

        // Unload plugin A - should succeed since no dependents
        boolean result = pluginManager.unloadPluginSafely("plugin.a");
        assertTrue(result, "Plugin should be unloaded successfully when no dependents exist");
        assertEquals(0, pluginManager.getResolvedPlugins().size());
        assertEquals(0, pluginManager.getPlugins().size());
    }

    @Test
    public void testCannotUnloadWithDependency() throws Exception {
        // Create plugin A and plugin B that depends on A
        PluginZip pluginA = new PluginZip.Builder(pluginsPath.resolve("A-plugin-1.2.3.zip"), "plugin.a")
            .pluginVersion("1.2.3").build();

        PluginZip pluginB = new PluginZip.Builder(pluginsPath.resolve("B-plugin-1.2.3.zip"), "plugin.b")
            .pluginDependencies("plugin.a")
            .pluginVersion("1.2.3").build();

        pluginManager.loadPlugins();
        assertEquals(2, pluginManager.getPlugins().size());

        pluginManager.startPlugins();
        assertEquals(2, pluginManager.getStartedPlugins().size());

        // Verify dependency relationship
        List<String> dependents = pluginManager.getDependents("plugin.a");
        assertEquals(1, dependents.size());
        assertTrue(dependents.contains("plugin.b"));

        // Try to unload plugin A safely - should fail because plugin B depends on it
        boolean result = pluginManager.unloadPluginSafely("plugin.a");
        assertFalse(result, "Should not unload plugin with dependents");
        assertEquals(2, pluginManager.getResolvedPlugins().size());
        assertEquals(2, pluginManager.getPlugins().size());
    }

    @Test
    public void testMultiLevelDependencyProtection() throws Exception {
        // Create plugin A, B depends on A, and C depends on B
        PluginZip pluginA = new PluginZip.Builder(pluginsPath.resolve("A-plugin-1.2.3.zip"), "plugin.a")
            .pluginVersion("1.2.3").build();

        PluginZip pluginB = new PluginZip.Builder(pluginsPath.resolve("B-plugin-1.2.3.zip"), "plugin.b")
            .pluginDependencies("plugin.a")
            .pluginVersion("1.2.3").build();

        PluginZip pluginC = new PluginZip.Builder(pluginsPath.resolve("C-plugin-1.2.3.zip"), "plugin.c")
            .pluginDependencies("plugin.b")
            .pluginVersion("1.2.3").build();

        pluginManager.loadPlugins();
        assertEquals(3, pluginManager.getPlugins().size());

        pluginManager.startPlugins();
        assertEquals(3, pluginManager.getStartedPlugins().size());

        // Verify dependency relationships
        List<String> dependentsA = pluginManager.getDependents("plugin.a");
        assertEquals(1, dependentsA.size());
        assertTrue(dependentsA.contains("plugin.b"));

        List<String> dependentsB = pluginManager.getDependents("plugin.b");
        assertEquals(1, dependentsB.size());
        assertTrue(dependentsB.contains("plugin.c"));

        // Try to unload plugin A safely - should fail
        boolean resultA = pluginManager.unloadPluginSafely("plugin.a");
        assertFalse(resultA, "Should not unload plugin with dependents");
        assertEquals(3, pluginManager.getPlugins().size());

        // Try to unload plugin B safely - should fail
        boolean resultB = pluginManager.unloadPluginSafely("plugin.b");
        assertFalse(resultB, "Should not unload plugin with dependents");
        assertEquals(3, pluginManager.getPlugins().size());

        // Unload plugin C first - should succeed
        boolean resultC = pluginManager.unloadPluginSafely("plugin.c");
        assertTrue(resultC, "Plugin without dependents should unload");
        assertEquals(2, pluginManager.getPlugins().size());

        // Now unload plugin B - should succeed
        boolean resultBAfter = pluginManager.unloadPluginSafely("plugin.b");
        assertTrue(resultBAfter, "Plugin should unload after dependents are gone");
        assertEquals(1, pluginManager.getPlugins().size());

        // Finally unload plugin A - should succeed
        boolean resultAAfter = pluginManager.unloadPluginSafely("plugin.a");
        assertTrue(resultAAfter, "Plugin should unload after dependents are gone");
        assertEquals(0, pluginManager.getPlugins().size());
    }
    
    @Test
    public void testAutoUnloadDependentsOption() throws Exception {
        // Create plugin A and plugin B that depends on A
        PluginZip pluginA = new PluginZip.Builder(pluginsPath.resolve("A-plugin-1.2.3.zip"), "plugin.a")
            .pluginVersion("1.2.3").build();

        PluginZip pluginB = new PluginZip.Builder(pluginsPath.resolve("B-plugin-1.2.3.zip"), "plugin.b")
            .pluginDependencies("plugin.a")
            .pluginVersion("1.2.3").build();

        pluginManager.loadPlugins();
        assertEquals(2, pluginManager.getPlugins().size());

        pluginManager.startPlugins();
        assertEquals(2, pluginManager.getStartedPlugins().size());
        
        // Check default value
        assertTrue(pluginManager.isAutoUnloadDependents());
        
        // Disable auto-unload of dependents
        pluginManager.setAutoUnloadDependents(false);
        assertFalse(pluginManager.isAutoUnloadDependents());
        
        // Now unloadPlugin should behave like unloadPluginSafely
        boolean result = pluginManager.unloadPlugin("plugin.a");
        assertFalse(result, "Should not unload plugin with dependents when autoUnloadDependents is false");
        assertEquals(2, pluginManager.getPlugins().size());
        
        // Enable auto-unload again
        pluginManager.setAutoUnloadDependents(true);
        assertTrue(pluginManager.isAutoUnloadDependents());
        
        // Now unloadPlugin should work as before
        boolean result2 = pluginManager.unloadPlugin("plugin.a");
        assertTrue(result2, "Should unload plugin with dependents when autoUnloadDependents is true");
        assertEquals(0, pluginManager.getPlugins().size());
    }
}

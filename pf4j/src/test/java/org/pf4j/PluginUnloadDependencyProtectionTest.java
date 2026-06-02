package org.pf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for plugin unload dependency protection mechanism.
 * Tests cover:
 * 1. Unload plugin with no dependencies (should succeed)
 * 2. Unload plugin with dependents (should fail)
 * 3. Multi-level dependency scenarios (A -> B -> C, unload B or C)
 */
class PluginUnloadDependencyProtectionTest {

    private DefaultPluginManager pluginManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pluginManager = new DefaultPluginManager(tempDir);
    }

    @Test
    @DisplayName("Should successfully unload plugin with no dependents")
    void unloadPluginWithNoDependents() {
        // Create plugin A with no dependencies
        PluginDescriptor descriptorA = createDescriptor("plugin-a", null);
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Load plugin A
        String pluginIdA = pluginManager.loadPlugin(pathA);
        assertNotNull(pluginIdA);
        assertEquals("plugin-a", pluginIdA);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        // Unload plugin A - should succeed since no other plugin depends on it
        boolean result = pluginManager.unloadPlugin("plugin-a");
        assertTrue(result, "Plugin with no dependents should be unloadable");

        // Verify plugin is removed
        assertNull(pluginManager.getPlugin("plugin-a"));
    }

    @Test
    @DisplayName("Should reject unload when another plugin depends on it (A -> B)")
    void unloadPluginWithDependent() {
        // Create plugin B (no dependencies)
        PluginDescriptor descriptorB = createDescriptor("plugin-b", null);
        Path pathB = tempDir.resolve("plugin-b");
        pluginManager.registerPluginDescriptor(descriptorB, pathB);

        // Create plugin A that depends on B
        PluginDescriptor descriptorA = createDescriptor("plugin-a", "plugin-b");
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Load plugins
        pluginManager.loadPlugin(pathB);
        pluginManager.loadPlugin(pathA);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        // Verify dependency resolution
        DependencyResolver resolver = pluginManager.getDependencyResolver();
        List<String> dependentsOfB = resolver.getDependents("plugin-b");
        assertEquals(1, dependentsOfB.size());
        assertTrue(dependentsOfB.contains("plugin-a"));

        // Try to unload plugin B - should fail because A depends on it
        boolean result = pluginManager.unloadPlugin("plugin-b");
        assertFalse(result, "Plugin with dependents should NOT be unloadable");

        // Verify plugin B is still loaded
        assertNotNull(pluginManager.getPlugin("plugin-b"));
        assertEquals(2, pluginManager.getPlugins().size());
    }

    @Test
    @DisplayName("Should allow unload of dependent plugin (A -> B, unload A)")
    void unloadDependentPlugin() {
        // Create plugin B (no dependencies)
        PluginDescriptor descriptorB = createDescriptor("plugin-b", null);
        Path pathB = tempDir.resolve("plugin-b");
        pluginManager.registerPluginDescriptor(descriptorB, pathB);

        // Create plugin A that depends on B
        PluginDescriptor descriptorA = createDescriptor("plugin-a", "plugin-b");
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Load plugins
        pluginManager.loadPlugin(pathB);
        pluginManager.loadPlugin(pathA);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        // Unload plugin A - should succeed since nothing depends on A
        boolean result = pluginManager.unloadPlugin("plugin-a");
        assertTrue(result, "Dependent plugin should be unloadable when nothing depends on it");

        // Verify plugin A is removed but B remains
        assertNull(pluginManager.getPlugin("plugin-a"));
        assertNotNull(pluginManager.getPlugin("plugin-b"));
    }

    @Test
    @DisplayName("Should handle multi-level dependencies (A -> B -> C)")
    void multiLevelDependencyChain() {
        // Create plugin C (no dependencies)
        PluginDescriptor descriptorC = createDescriptor("plugin-c", null);
        Path pathC = tempDir.resolve("plugin-c");
        pluginManager.registerPluginDescriptor(descriptorC, pathC);

        // Create plugin B that depends on C
        PluginDescriptor descriptorB = createDescriptor("plugin-b", "plugin-c");
        Path pathB = tempDir.resolve("plugin-b");
        pluginManager.registerPluginDescriptor(descriptorB, pathB);

        // Create plugin A that depends on B
        PluginDescriptor descriptorA = createDescriptor("plugin-a", "plugin-b");
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Load plugins
        pluginManager.loadPlugin(pathC);
        pluginManager.loadPlugin(pathB);
        pluginManager.loadPlugin(pathA);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        // Verify dependency chain
        DependencyResolver resolver = pluginManager.getDependencyResolver();

        // B and A depend on C
        List<String> dependentsOfC = resolver.getDependents("plugin-c");
        assertEquals(1, dependentsOfC.size());
        assertTrue(dependentsOfC.contains("plugin-b"));

        // A depends on B
        List<String> dependentsOfB = resolver.getDependents("plugin-b");
        assertEquals(1, dependentsOfB.size());
        assertTrue(dependentsOfB.contains("plugin-a"));

        // Nothing depends on A
        List<String> dependentsOfA = resolver.getDependents("plugin-a");
        assertTrue(dependentsOfA.isEmpty());

        // Try to unload C - should fail (B depends on it)
        assertFalse(pluginManager.unloadPlugin("plugin-c"),
                "Should not unload C because B depends on it");

        // Try to unload B - should fail (A depends on it)
        assertFalse(pluginManager.unloadPlugin("plugin-b"),
                "Should not unload B because A depends on it");

        // Unload A - should succeed
        assertTrue(pluginManager.unloadPlugin("plugin-a"),
                "Should be able to unload A");

        // After A is unloaded, B can be unloaded
        assertTrue(pluginManager.unloadPlugin("plugin-b"),
                "After A is unloaded, B should be unloadable");

        // After B is unloaded, C can be unloaded
        assertTrue(pluginManager.unloadPlugin("plugin-c"),
                "After B is unloaded, C should be unloadable");

        // All plugins should be unloaded
        assertEquals(0, pluginManager.getPlugins().size());
    }

    @Test
    @DisplayName("Should detect transitive dependents (A -> B -> C, check C's transitive dependents)")
    void transitiveDependentsDetection() {
        // Create plugin C (no dependencies)
        PluginDescriptor descriptorC = createDescriptor("plugin-c", null);
        Path pathC = tempDir.resolve("plugin-c");
        pluginManager.registerPluginDescriptor(descriptorC, pathC);

        // Create plugin B that depends on C
        PluginDescriptor descriptorB = createDescriptor("plugin-b", "plugin-c");
        Path pathB = tempDir.resolve("plugin-b");
        pluginManager.registerPluginDescriptor(descriptorB, pathB);

        // Create plugin A that depends on B
        PluginDescriptor descriptorA = createDescriptor("plugin-a", "plugin-b");
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Load plugins
        pluginManager.loadPlugin(pathC);
        pluginManager.loadPlugin(pathB);
        pluginManager.loadPlugin(pathA);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        // Check transitive dependents of C
        DependencyResolver resolver = pluginManager.getDependencyResolver();
        List<String> transitiveDependentsOfC = resolver.getTransitiveDependents("plugin-c");

        // C has B as direct dependent, and A as transitive dependent (through B)
        assertEquals(2, transitiveDependentsOfC.size());
        assertTrue(transitiveDependentsOfC.contains("plugin-b"));
        assertTrue(transitiveDependentsOfC.contains("plugin-a"));

        // Check transitive dependents of B
        List<String> transitiveDependentsOfB = resolver.getTransitiveDependents("plugin-b");
        assertEquals(1, transitiveDependentsOfB.size());
        assertTrue(transitiveDependentsOfB.contains("plugin-a"));
    }

    @Test
    @DisplayName("Should handle multiple dependents (A -> C, B -> C)")
    void multipleDependentsOnSamePlugin() {
        // Create plugin C (no dependencies)
        PluginDescriptor descriptorC = createDescriptor("plugin-c", null);
        Path pathC = tempDir.resolve("plugin-c");
        pluginManager.registerPluginDescriptor(descriptorC, pathC);

        // Create plugin A that depends on C
        PluginDescriptor descriptorA = createDescriptor("plugin-a", "plugin-c");
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Create plugin B that also depends on C
        PluginDescriptor descriptorB = createDescriptor("plugin-b", "plugin-c");
        Path pathB = tempDir.resolve("plugin-b");
        pluginManager.registerPluginDescriptor(descriptorB, pathB);

        // Load plugins
        pluginManager.loadPlugin(pathC);
        pluginManager.loadPlugin(pathA);
        pluginManager.loadPlugin(pathB);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        // Both A and B depend on C
        DependencyResolver resolver = pluginManager.getDependencyResolver();
        List<String> dependentsOfC = resolver.getDependents("plugin-c");
        assertEquals(2, dependentsOfC.size());
        assertTrue(dependentsOfC.contains("plugin-a"));
        assertTrue(dependentsOfC.contains("plugin-b"));

        // Cannot unload C because both A and B depend on it
        assertFalse(pluginManager.unloadPlugin("plugin-c"),
                "Should not unload C because both A and B depend on it");

        // Unload A first
        assertTrue(pluginManager.unloadPlugin("plugin-a"));

        // Still cannot unload C because B still depends on it
        assertFalse(pluginManager.unloadPlugin("plugin-c"),
                "Should still not unload C because B still depends on it");

        // Unload B
        assertTrue(pluginManager.unloadPlugin("plugin-b"));

        // Now C can be unloaded
        assertTrue(pluginManager.unloadPlugin("plugin-c"),
                "After A and B are unloaded, C should be unloadable");
    }

    @Test
    @DisplayName("Should return false when trying to unload non-existent plugin")
    void unloadNonExistentPlugin() {
        boolean result = pluginManager.unloadPlugin("non-existent-plugin");
        assertFalse(result);
    }

    @Test
    @DisplayName("Should allow unload with dependency check disabled")
    void unloadWithDependencyCheckDisabled() {
        // Create plugin B (no dependencies)
        PluginDescriptor descriptorB = createDescriptor("plugin-b", null);
        Path pathB = tempDir.resolve("plugin-b");
        pluginManager.registerPluginDescriptor(descriptorB, pathB);

        // Create plugin A that depends on B
        PluginDescriptor descriptorA = createDescriptor("plugin-a", "plugin-b");
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Load plugins
        pluginManager.loadPlugin(pathB);
        pluginManager.loadPlugin(pathA);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        // Unload B with dependency check disabled - should succeed
        boolean result = pluginManager.unloadPlugin("plugin-b", false);
        assertTrue(result, "Should unload when dependency check is disabled");

        // Verify B is unloaded but A remains
        assertNull(pluginManager.getPlugin("plugin-b"));
        assertNotNull(pluginManager.getPlugin("plugin-a"));
    }

    @Test
    @DisplayName("Should handle diamond dependency pattern (A -> B, A -> C, B -> D, C -> D)")
    void diamondDependencyPattern() {
        // Create plugin D (no dependencies)
        PluginDescriptor descriptorD = createDescriptor("plugin-d", null);
        Path pathD = tempDir.resolve("plugin-d");
        pluginManager.registerPluginDescriptor(descriptorD, pathD);

        // Create plugin B that depends on D
        PluginDescriptor descriptorB = createDescriptor("plugin-b", "plugin-d");
        Path pathB = tempDir.resolve("plugin-b");
        pluginManager.registerPluginDescriptor(descriptorB, pathB);

        // Create plugin C that depends on D
        PluginDescriptor descriptorC = createDescriptor("plugin-c", "plugin-d");
        Path pathC = tempDir.resolve("plugin-c");
        pluginManager.registerPluginDescriptor(descriptorC, pathC);

        // Create plugin A that depends on B and C
        PluginDescriptor descriptorA = createDescriptor("plugin-a", "plugin-b,plugin-c");
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Load plugins
        pluginManager.loadPlugin(pathD);
        pluginManager.loadPlugin(pathB);
        pluginManager.loadPlugin(pathC);
        pluginManager.loadPlugin(pathA);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        // Verify dependencies
        DependencyResolver resolver = pluginManager.getDependencyResolver();

        // D has B and C as dependents
        List<String> dependentsOfD = resolver.getDependents("plugin-d");
        assertEquals(2, dependentsOfD.size());
        assertTrue(dependentsOfD.contains("plugin-b"));
        assertTrue(dependentsOfD.contains("plugin-c"));

        // Cannot unload D (B and C depend on it)
        assertFalse(pluginManager.unloadPlugin("plugin-d"));

        // Cannot unload B (A depends on it)
        assertFalse(pluginManager.unloadPlugin("plugin-b"));

        // Cannot unload C (A depends on it)
        assertFalse(pluginManager.unloadPlugin("plugin-c"));

        // Can unload A
        assertTrue(pluginManager.unloadPlugin("plugin-a"));

        // After A is unloaded, can unload B and C
        assertTrue(pluginManager.unloadPlugin("plugin-b"));
        assertTrue(pluginManager.unloadPlugin("plugin-c"));

        // After B and C are unloaded, can unload D
        assertTrue(pluginManager.unloadPlugin("plugin-d"));

        assertEquals(0, pluginManager.getPlugins().size());
    }

    @Test
    @DisplayName("DependencyResolver.canUnload should return correct result")
    void dependencyResolverCanUnload() {
        // Create plugin B (no dependencies)
        PluginDescriptor descriptorB = createDescriptor("plugin-b", null);
        Path pathB = tempDir.resolve("plugin-b");
        pluginManager.registerPluginDescriptor(descriptorB, pathB);

        // Create plugin A that depends on B
        PluginDescriptor descriptorA = createDescriptor("plugin-a", "plugin-b");
        Path pathA = tempDir.resolve("plugin-a");
        pluginManager.registerPluginDescriptor(descriptorA, pathA);

        // Load plugins
        pluginManager.loadPlugin(pathB);
        pluginManager.loadPlugin(pathA);

        // Resolve dependencies
        pluginManager.resolveDependencies();

        DependencyResolver resolver = pluginManager.getDependencyResolver();

        // B cannot be unloaded (A depends on it)
        assertFalse(resolver.canUnload("plugin-b"));

        // A can be unloaded (nothing depends on it)
        assertTrue(resolver.canUnload("plugin-a"));
    }

    // Helper method to create plugin descriptors
    private PluginDescriptor createDescriptor(String pluginId, String dependencies) {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setPluginId(pluginId);
        descriptor.setPluginClass("org.pf4j.TestPlugin");
        descriptor.setVersion("1.0.0");
        descriptor.setDependencies(dependencies);
        return descriptor;
    }
}

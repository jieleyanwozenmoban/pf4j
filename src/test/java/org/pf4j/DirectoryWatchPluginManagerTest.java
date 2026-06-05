package org.pf4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.test.PluginZip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DirectoryWatchPluginManagerTest {

    private DirectoryWatchPluginManager pluginManager;

    @TempDir
    Path pluginsPath;

    @BeforeEach
    public void setUp() {
        pluginManager = new DirectoryWatchPluginManager(pluginsPath);
    }

    @AfterEach
    public void tearDown() {
        if (pluginManager != null) {
            pluginManager.stopPlugins();
        }
    }

    @Test
    public void testWatchAddDeleteUpgrade() throws Exception {
        // Test Add
        Path zipPath = pluginsPath.resolve("my-plugin-1.2.3.zip");
        PluginZip pluginZip = new PluginZip.Builder(zipPath, "myPlugin")
                .pluginVersion("1.2.3")
                .addFile(Paths.get("META-INF/extensions.idx"), "org.pf4j.test.TestExtension\n")
                .build();

        // wait for watcher to detect the new file
        awaitPluginsCount(1, 5000);
        
        List<PluginWrapper> plugins = pluginManager.getPlugins();
        assertEquals(1, plugins.size());
        assertEquals("myPlugin", plugins.get(0).getPluginId());
        assertEquals(PluginState.STARTED, plugins.get(0).getPluginState());
        assertEquals("1.2.3", plugins.get(0).getDescriptor().getVersion());
        
        // Verify extension point works
        List<org.pf4j.test.TestExtensionPoint> extensions = pluginManager.getExtensions(org.pf4j.test.TestExtensionPoint.class);
        assertEquals(1, extensions.size());

        // Test Upgrade (Replace)
        Path newZipPath = pluginsPath.resolve("my-plugin-2.0.0.zip");
        PluginZip newPluginZip = new PluginZip.Builder(newZipPath, "myPlugin")
                .pluginVersion("2.0.0")
                .addFile(Paths.get("META-INF/extensions.idx"), "org.pf4j.test.TestExtension\n")
                .build();
        
        // Remove old file to simulate upgrade
        Files.delete(zipPath);

        // Wait for watcher to process modify/delete/create
        awaitPluginVersion("2.0.0", 5000);

        plugins = pluginManager.getPlugins();
        assertEquals(1, plugins.size());
        assertEquals("myPlugin", plugins.get(0).getPluginId());
        assertEquals(PluginState.STARTED, plugins.get(0).getPluginState());
        assertEquals("2.0.0", plugins.get(0).getDescriptor().getVersion());
        
        // Verify extension point still works
        extensions = pluginManager.getExtensions(org.pf4j.test.TestExtensionPoint.class);
        assertEquals(1, extensions.size());

        // Test Delete
        Files.delete(newZipPath);
        
        awaitPluginsCount(0, 5000);

        plugins = pluginManager.getPlugins();
        assertEquals(0, plugins.size());
        
        // Verify extension point is gone
        extensions = pluginManager.getExtensions(org.pf4j.test.TestExtensionPoint.class);
        assertEquals(0, extensions.size());
    }



    private void awaitPluginsCount(int expectedCount, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (pluginManager.getPlugins().size() == expectedCount) {
                return;
            }
            Thread.sleep(100);
        }
    }

    private void awaitPluginVersion(String expectedVersion, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            List<PluginWrapper> plugins = pluginManager.getPlugins();
            if (plugins.size() == 1 && plugins.get(0).getDescriptor().getVersion().equals(expectedVersion)) {
                return;
            }
            Thread.sleep(100);
        }
    }
}
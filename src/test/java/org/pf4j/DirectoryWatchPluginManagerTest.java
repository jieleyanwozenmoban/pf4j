package org.pf4j;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.test.ClassDataProvider;
import org.pf4j.test.DefaultClassDataProvider;
import org.pf4j.test.JavaFileObjectUtils;
import org.pf4j.test.JavaSources;
import org.pf4j.test.PluginJar;
import org.pf4j.test.TestExtensionPoint;
import org.pf4j.test.TestPlugin;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectoryWatchPluginManagerTest {

    @TempDir
    Path pluginsPath;

    private DirectoryWatchPluginManager pluginManager;

    @AfterEach
    void tearDown() {
        if (pluginManager != null) {
            pluginManager.close();
            pluginManager.unloadPlugins();
        }
    }

    @Test
    void addsPluginAndRefreshesExtensions() throws Exception {
        pluginManager = new DirectoryWatchPluginManager(pluginsPath);
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        assertExtensionMessages();

        createPluginJar("1.0.0", "Hello from v1");

        assertExtensionMessages("Hello from v1");
        assertEquals(1, pluginManager.getPlugins().size());
        assertEquals(1, pluginManager.getStartedPlugins().size());
    }

    @Test
    void deletesPluginAndRemovesExtensions() throws Exception {
        PluginJar pluginJar = createPluginJar("1.0.0", "Hello from v1");

        pluginManager = new DirectoryWatchPluginManager(pluginsPath);
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        assertExtensionMessages("Hello from v1");

        Files.delete(pluginJar.path());

        assertExtensionMessages();
        assertEquals(0, pluginManager.getPlugins().size());
        assertEquals(0, pluginManager.getStartedPlugins().size());
    }

    @Test
    void upgradesPluginAndRefreshesExtensions() throws Exception {
        createPluginJar("1.0.0", "Hello from v1");

        pluginManager = new DirectoryWatchPluginManager(pluginsPath);
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        assertExtensionMessages("Hello from v1");
        assertEquals("1.0.0", pluginManager.getPlugin("watch-plugin").getDescriptor().getVersion());

        createPluginJar("2.0.0", "Hello from v2");

        waitUntil(() -> pluginManager.getPlugin("watch-plugin") != null
            && "2.0.0".equals(pluginManager.getPlugin("watch-plugin").getDescriptor().getVersion())
            && extensionMessages().equals(List.of("Hello from v2")));

        assertEquals("2.0.0", pluginManager.getPlugin("watch-plugin").getDescriptor().getVersion());
        assertEquals(1, pluginManager.getStartedPlugins().size());
    }

    private PluginJar createPluginJar(String version, String message) throws IOException {
        String extensionClassName = "org.pf4j.watch.WatchExtension";
        JavaFileObject extensionSource = JavaFileObjects.forSourceLines(
            extensionClassName,
            "package org.pf4j.watch;",
            "import org.pf4j.Extension;",
            "import org.pf4j.test.TestExtensionPoint;",
            "@Extension",
            "public class WatchExtension implements TestExtensionPoint {",
            "    @Override",
            "    public String saySomething() {",
            "        return \"" + message + "\";",
            "    }",
            "}"
        );

        Map<String, JavaFileObject> generatedClasses = JavaSources.compileAll(extensionSource).stream()
            .filter(javaFileObject -> javaFileObject.getKind() == JavaFileObject.Kind.CLASS)
            .collect(Collectors.toMap(JavaFileObjectUtils::getClassName, javaFileObject -> javaFileObject));

        ClassDataProvider defaultClassDataProvider = new DefaultClassDataProvider();
        ClassDataProvider classDataProvider = className -> TestPlugin.class.getName().equals(className)
            ? defaultClassDataProvider.getClassData(className)
            : JavaFileObjectUtils.getAllBytes(generatedClasses.get(className));

        return new PluginJar.Builder(pluginsPath.resolve("watch-plugin.jar"), "watch-plugin")
            .pluginClass(TestPlugin.class.getName())
            .pluginVersion(version)
            .classDataProvider(classDataProvider)
            .extension(extensionClassName)
            .build();
    }

    private void assertExtensionMessages(String... expectedMessages) {
        waitUntil(() -> extensionMessages().equals(List.of(expectedMessages)));
    }

    private List<String> extensionMessages() {
        return pluginManager.getExtensions(TestExtensionPoint.class).stream()
            .map(TestExtensionPoint::saySomething)
            .sorted()
            .collect(Collectors.toList());
    }

    private void waitUntil(Condition condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (condition.matches()) {
                return;
            }

            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }

        assertEquals(true, condition.matches());
    }

    @FunctionalInterface
    private interface Condition {

        boolean matches();

    }

}

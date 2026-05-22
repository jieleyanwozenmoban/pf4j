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

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.processor.LegacyExtensionStorage;
import org.pf4j.test.JavaFileObjectUtils;
import org.pf4j.test.JavaSources;
import org.pf4j.test.PluginZip;
import org.pf4j.test.TestExtension;
import org.pf4j.util.FileUtils;

import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sebastian Lövdahl
 */
class PluginClassLoaderTest {

    private static final String CONFLICT_CLASS_NAME = TestExtension.class.getName();
    private static final String DEPENDENCY_CONFLICT_MESSAGE = "dependency";
    private static final String PLUGIN_CONFLICT_MESSAGE = "plugin";
    private static final String PARENT_CONFLICT_MESSAGE = "I am a test extension";

    private TestPluginManager pluginManager;
    private TestPluginManager pluginManagerParentFirst;
    private DefaultPluginDescriptor pluginDependencyDescriptor;
    private DefaultPluginDescriptor pluginDescriptor;

    private PluginClassLoader parentLastPluginClassLoader;
    private PluginClassLoader parentFirstPluginClassLoader;

    private PluginClassLoader parentLastPluginDependencyClassLoader;
    private PluginClassLoader parentFirstPluginDependencyClassLoader;

    private PluginZip pluginDependencyZip;

    @TempDir
    Path pluginsPath;

    @BeforeAll
    static void setUpGlobal() throws IOException, URISyntaxException {
        Path parentClassPathBase = Paths.get(PluginClassLoaderTest.class.getClassLoader().getResource(".").toURI());

        Path metaInfPath = parentClassPathBase.resolve("META-INF");
        File metaInfFile = metaInfPath.toFile();
        if (metaInfFile.mkdir()) {
            metaInfFile.deleteOnExit();
        }

        createFile(metaInfPath.resolve("file-only-in-parent"));
        createFile(metaInfPath.resolve("file-in-both-parent-and-dependency-and-plugin"));
        createFile(metaInfPath.resolve("file-in-both-parent-and-dependency"));
        createFile(metaInfPath.resolve("file-in-both-parent-and-plugin"));
        createFile(parentClassPathBase.resolve(LegacyExtensionStorage.EXTENSIONS_RESOURCE));
    }

    private static void createFile(Path pathToFile) throws IOException {
        File file = pathToFile.toFile();

        file.deleteOnExit();
        assertTrue(file.exists() || file.createNewFile(), "failed to create '" + pathToFile + "'");
        try (PrintWriter printWriter = new PrintWriter(file)) {
            printWriter.write("parent");
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        pluginManager = new TestPluginManager(pluginsPath);
        pluginManagerParentFirst = new TestPluginManager(pluginsPath);

        pluginDependencyDescriptor = new DefaultPluginDescriptor()
            .setPluginId("myDependency")
            .setPluginVersion("1.2.3")
            .setPluginDescription("My plugin")
            .setDependencies("")
            .setProvider("Me")
            .setRequires("5.0.0");

        Map<String, JavaFileObject> generatedClasses = JavaSources.compileAll(JavaSources.GREETING, JavaSources.WHAZZUP_GREETING)
            .stream()
            .map(javaFileObject -> new AbstractMap.SimpleEntry<>(JavaFileObjectUtils.getClassName(javaFileObject), javaFileObject))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        JavaFileObject dependencyConflictClass = JavaSources.compile(createConflictClassSource(DEPENDENCY_CONFLICT_MESSAGE));
        JavaFileObject pluginConflictClass = JavaSources.compile(createConflictClassSource(PLUGIN_CONFLICT_MESSAGE));

        Path classesPath = Paths.get("classes");
        Path metaInfPath = classesPath.resolve("META-INF");

        Path greetingClassPath = classesPath.resolve(JavaSources.GREETING_CLASS_NAME.replace('.', '/') + ".class");
        Path whaszzupGreetingClassPath = classesPath.resolve(JavaSources.WHAZZUP_GREETING_CLASS_NAME.replace('.', '/') + ".class");
        Path conflictClassPath = classesPath.resolve(CONFLICT_CLASS_NAME.replace('.', '/') + ".class");

        Path pluginDependencyPath = pluginsPath.resolve(pluginDependencyDescriptor.getPluginId() + "-" + pluginDependencyDescriptor.getVersion() + ".zip");
        pluginDependencyZip = new PluginZip.Builder(pluginDependencyPath, pluginDependencyDescriptor.getPluginId())
                .pluginVersion(pluginDependencyDescriptor.getVersion())
                .addFile(metaInfPath.resolve("dependency-file"), "dependency")
                .addFile(metaInfPath.resolve("file-in-both-parent-and-dependency-and-plugin"), "dependency")
                .addFile(metaInfPath.resolve("file-in-both-parent-and-dependency"), "dependency")
                .addFile(classesPath.resolve(LegacyExtensionStorage.EXTENSIONS_RESOURCE), "dependency")
                .addFile(greetingClassPath, JavaFileObjectUtils.getAllBytes(generatedClasses.get(JavaSources.GREETING_CLASS_NAME)))
                .addFile(whaszzupGreetingClassPath, JavaFileObjectUtils.getAllBytes(generatedClasses.get(JavaSources.WHAZZUP_GREETING_CLASS_NAME)))
                .addFile(conflictClassPath, JavaFileObjectUtils.getAllBytes(dependencyConflictClass))
                .build();

        pluginDependencyZip.unzip();

        PluginClasspath pluginDependencyClasspath = new DefaultPluginClasspath();

        parentLastPluginDependencyClassLoader = new PluginClassLoader(pluginManager, pluginDependencyDescriptor, PluginClassLoaderTest.class.getClassLoader());
        parentFirstPluginDependencyClassLoader = new PluginClassLoader(pluginManagerParentFirst, pluginDependencyDescriptor, PluginClassLoaderTest.class.getClassLoader(), true);

        pluginManager.addClassLoader(pluginDependencyDescriptor.getPluginId(), parentLastPluginDependencyClassLoader);
        pluginManagerParentFirst.addClassLoader(pluginDependencyDescriptor.getPluginId(), parentFirstPluginDependencyClassLoader);

        for (String classesDirectory : pluginDependencyClasspath.getClassesDirectories()) {
            File classesDirectoryFile = pluginDependencyZip.unzippedPath().resolve(classesDirectory).toFile();
            parentLastPluginDependencyClassLoader.addFile(classesDirectoryFile);
            parentFirstPluginDependencyClassLoader.addFile(classesDirectoryFile);
        }

        for (String jarsDirectory : pluginDependencyClasspath.getJarsDirectories()) {
            Path jarsDirectoryPath = pluginDependencyZip.unzippedPath().resolve(jarsDirectory);
            List<File> jars = FileUtils.getJars(jarsDirectoryPath);
            for (File jar : jars) {
                parentLastPluginDependencyClassLoader.addFile(jar);
                parentFirstPluginDependencyClassLoader.addFile(jar);
            }
        }

        pluginDescriptor = new DefaultPluginDescriptor();
        pluginDescriptor.setPluginId("myPlugin");
        pluginDescriptor.setPluginVersion("1.2.3");
        pluginDescriptor.setPluginDescription("My plugin");
        pluginDescriptor.setDependencies("myDependency");
        pluginDescriptor.setProvider("Me");
        pluginDescriptor.setRequires("5.0.0");

        Path pluginPath = pluginsPath.resolve(pluginDescriptor.getPluginId() + "-" + pluginDescriptor.getVersion() + ".zip");
        PluginZip pluginZip = new PluginZip.Builder(pluginPath, pluginDescriptor.getPluginId())
                .pluginVersion(pluginDescriptor.getVersion())
                .addFile(metaInfPath.resolve("plugin-file"), "plugin")
                .addFile(metaInfPath.resolve("file-in-both-parent-and-dependency-and-plugin"), "plugin")
                .addFile(metaInfPath.resolve("file-in-both-parent-and-plugin"), "plugin")
                .addFile(classesPath.resolve(LegacyExtensionStorage.EXTENSIONS_RESOURCE), "plugin")
                .addFile(conflictClassPath, JavaFileObjectUtils.getAllBytes(pluginConflictClass))
                .build();

        pluginZip.unzip();

        PluginClasspath pluginClasspath = new DefaultPluginClasspath();

        parentLastPluginClassLoader = new PluginClassLoader(pluginManager, pluginDescriptor, PluginClassLoaderTest.class.getClassLoader());
        parentFirstPluginClassLoader = new PluginClassLoader(pluginManager, pluginDescriptor, PluginClassLoaderTest.class.getClassLoader(), true);

        pluginManager.addClassLoader(pluginDescriptor.getPluginId(), parentLastPluginClassLoader);
        pluginManagerParentFirst.addClassLoader(pluginDescriptor.getPluginId(), parentFirstPluginClassLoader);

        for (String classesDirectory : pluginClasspath.getClassesDirectories()) {
            File classesDirectoryFile = pluginZip.unzippedPath().resolve(classesDirectory).toFile();
            parentLastPluginClassLoader.addFile(classesDirectoryFile);
            parentFirstPluginClassLoader.addFile(classesDirectoryFile);
        }

        for (String jarsDirectory : pluginClasspath.getJarsDirectories()) {
            Path jarsDirectoryPath = pluginZip.unzippedPath().resolve(jarsDirectory);
            List<File> jars = FileUtils.getJars(jarsDirectoryPath);
            for (File jar : jars) {
                parentLastPluginClassLoader.addFile(jar);
                parentFirstPluginClassLoader.addFile(jar);
            }
        }
    }

    @AfterEach
    void tearDown() {
        pluginManager = null;
        pluginDependencyDescriptor = null;
        pluginDescriptor = null;
        parentLastPluginClassLoader =  null;
        parentFirstPluginClassLoader = null;
    }

    @Test
    void parentLastGetResourceNonExisting() {
        assertNull(parentLastPluginClassLoader.getResource("META-INF/non-existing-file"));
    }

    @Test
    void parentFirstGetResourceNonExisting() {
        assertNull(parentFirstPluginClassLoader.getResource("META-INF/non-existing-file"));
    }

    @Test
    void parentLastGetResourceExistsInParent() throws IOException, URISyntaxException {
        URL resource = parentLastPluginClassLoader.getResource("META-INF/file-only-in-parent");
        assertFirstLine("parent", resource);
    }

    @Test
    void parentFirstGetResourceExistsInParent() throws IOException, URISyntaxException {
        URL resource = parentFirstPluginClassLoader.getResource("META-INF/file-only-in-parent");
        assertFirstLine("parent", resource);
    }

    @Test
    void parentLastGetResourceExistsOnlyInPlugin() throws IOException, URISyntaxException {
        URL resource = parentLastPluginClassLoader.getResource("META-INF/plugin-file");
        assertFirstLine("plugin", resource);
    }

    @Test
    void parentFirstGetResourceExistsOnlyInPlugin() throws IOException, URISyntaxException {
        URL resource = parentFirstPluginClassLoader.getResource("META-INF/plugin-file");
        assertFirstLine("plugin", resource);
    }

    @Test
    void parentLastGetResourceExistsOnlyInDependnecy() throws IOException, URISyntaxException {
        URL resource = parentLastPluginClassLoader.getResource("META-INF/dependency-file");
        assertFirstLine("dependency", resource);
    }

    @Test
    void parentFirstGetResourceExistsOnlyInDependency() throws IOException, URISyntaxException {
        URL resource = parentFirstPluginClassLoader.getResource("META-INF/dependency-file");
        assertFirstLine("dependency", resource);
    }

    @Test
    void parentLastGetResourceExistsInBothParentAndPlugin() throws URISyntaxException, IOException {
        URL resource = parentLastPluginClassLoader.getResource("META-INF/file-in-both-parent-and-plugin");
        assertFirstLine("plugin", resource);
    }

    @Test
    void parentFirstGetResourceExistsInBothParentAndPlugin() throws URISyntaxException, IOException {
        URL resource = parentFirstPluginClassLoader.getResource("META-INF/file-in-both-parent-and-plugin");
        assertFirstLine("parent", resource);
    }

    @Test
    void parentLastGetResourceExistsInParentAndDependencyAndPlugin() throws URISyntaxException, IOException {
        URL resource = parentLastPluginClassLoader.getResource("META-INF/file-in-both-parent-and-dependency-and-plugin");
        assertFirstLine("plugin", resource);
    }

    @Test
    void parentFirstGetResourceExistsInParentAndDependencyAndPlugin() throws URISyntaxException, IOException {
        URL resource = parentFirstPluginClassLoader.getResource("META-INF/file-in-both-parent-and-dependency-and-plugin");
        assertFirstLine("parent", resource);
    }

    @Test
    void parentLastGetResourcesNonExisting() throws IOException {
        assertFalse(parentLastPluginClassLoader.getResources("META-INF/non-existing-file").hasMoreElements());
    }

    @Test
    void parentFirstGetResourcesNonExisting() throws IOException {
        assertFalse(parentFirstPluginClassLoader.getResources("META-INF/non-existing-file").hasMoreElements());
    }

    @Test
    void parentLastGetResourcesExistsInParent() throws IOException, URISyntaxException {
        Enumeration<URL> resources = parentLastPluginClassLoader.getResources("META-INF/file-only-in-parent");
        assertNumberOfResourcesAndFirstLineOfFirstElement(1, "parent", resources);
    }

    @Test
    void parentFirstGetResourcesExistsInParent() throws IOException, URISyntaxException {
        Enumeration<URL> resources = parentFirstPluginClassLoader.getResources("META-INF/file-only-in-parent");
        assertNumberOfResourcesAndFirstLineOfFirstElement(1, "parent", resources);
    }

    @Test
    void parentLastGetResourcesExistsOnlyInDependency() throws IOException, URISyntaxException {
        Enumeration<URL> resources = parentLastPluginClassLoader.getResources("META-INF/dependency-file");
        assertNumberOfResourcesAndFirstLineOfFirstElement(1, "dependency", resources);
    }

    @Test
    void parentFirstGetResourcesExistsOnlyInDependency() throws IOException, URISyntaxException {
        Enumeration<URL> resources = parentFirstPluginClassLoader.getResources("META-INF/dependency-file");
        assertNumberOfResourcesAndFirstLineOfFirstElement(1, "dependency", resources);
    }

    @Test
    void parentLastGetResourcesExistsOnlyInPlugin() throws IOException, URISyntaxException {
        Enumeration<URL> resources = parentLastPluginClassLoader.getResources("META-INF/plugin-file");
        assertNumberOfResourcesAndFirstLineOfFirstElement(1, "plugin", resources);
    }

    @Test
    void parentFirstGetResourcesExistsOnlyInPlugin() throws IOException, URISyntaxException {
        Enumeration<URL> resources = parentFirstPluginClassLoader.getResources("META-INF/plugin-file");
        assertNumberOfResourcesAndFirstLineOfFirstElement(1, "plugin", resources);
    }

    @Test
    void parentLastGetResourcesExistsInBothParentAndPlugin() throws URISyntaxException, IOException {
        Enumeration<URL> resources = parentLastPluginClassLoader.getResources("META-INF/file-in-both-parent-and-plugin");
        assertNumberOfResourcesAndFirstLineOfFirstElement(2, "plugin", resources);
    }

    @Test
    void parentFirstGetResourcesExistsInBothParentAndPlugin() throws URISyntaxException, IOException {
        Enumeration<URL> resources = parentFirstPluginClassLoader.getResources("META-INF/file-in-both-parent-and-plugin");
        assertNumberOfResourcesAndFirstLineOfFirstElement(2, "parent", resources);
    }

    @Test
    void parentLastGetResourcesExistsInParentAndDependencyAndPlugin() throws URISyntaxException, IOException {
        Enumeration<URL> resources = parentLastPluginClassLoader.getResources("META-INF/file-in-both-parent-and-dependency-and-plugin");
        assertNumberOfResourcesAndFirstLineOfFirstElement(3, "plugin", resources);
    }

    @Test
    void parentFirstGetResourcesExistsInParentAndDependencyAndPlugin() throws URISyntaxException, IOException {
        Enumeration<URL> resources = parentFirstPluginClassLoader.getResources("META-INF/file-in-both-parent-and-dependency-and-plugin");
        assertNumberOfResourcesAndFirstLineOfFirstElement(3, "parent", resources);
    }

    @Test
    void parentFirstGetExtensionsIndexExistsInParentAndDependencyAndPlugin() throws URISyntaxException, IOException {
        URL resource = parentLastPluginClassLoader.getResource(LegacyExtensionFinder.EXTENSIONS_RESOURCE);
        assertFirstLine("plugin", resource);
    }

    @Test
    void parentLastGetExtensionsIndexExistsInParentAndDependencyAndPlugin() throws URISyntaxException, IOException {
        URL resource = parentLastPluginClassLoader.getResource(LegacyExtensionFinder.EXTENSIONS_RESOURCE);
        assertFirstLine("plugin", resource);
    }

    @Test
    void parentLastLoadClassPrefersCurrentPluginOverApplicationAndDependencyOnConflict() throws Exception {
        Class<?> loadedClass = parentLastPluginClassLoader.loadClass(CONFLICT_CLASS_NAME);

        assertSame(parentLastPluginClassLoader, loadedClass.getClassLoader());
        assertEquals(PLUGIN_CONFLICT_MESSAGE, invokeSaySomething(loadedClass));
    }

    @Test
    void parentLastLoadClassPrefersApplicationOverDependencyOnConflict() throws Exception {
        PluginClassLoader pluginClassLoader = createPluginClassLoader("applicationWins", "myDependency");

        Class<?> loadedClass = pluginClassLoader.loadClass(CONFLICT_CLASS_NAME);

        assertSame(TestExtension.class, loadedClass);
        assertEquals(PARENT_CONFLICT_MESSAGE, invokeSaySomething(loadedClass));
    }

    @Test
    void parentLastLoadClassLoadsDependencyWhenApplicationAndPluginMiss() throws Exception {
        PluginClassLoader pluginClassLoader = createPluginClassLoader("dependencyWinsWhenOnlyDependencyHasClass", "myDependency");

        Class<?> loadedClass = pluginClassLoader.loadClass(JavaSources.GREETING_CLASS_NAME);

        assertSame(parentLastPluginDependencyClassLoader, loadedClass.getClassLoader());
        assertEquals(JavaSources.GREETING_CLASS_NAME, loadedClass.getName());
    }

    @Test
    void isClosed() throws IOException {
        parentLastPluginClassLoader.close();
        assertTrue(parentLastPluginClassLoader.isClosed());
    }

    @Test
    void collectClassLoader() throws IOException, ClassNotFoundException, InterruptedException {
        PluginClassLoader classLoader = new PluginClassLoader(pluginManager, pluginDependencyDescriptor, PluginClassLoaderTest.class.getClassLoader());
        PluginClasspath pluginDependencyClasspath = new DefaultPluginClasspath();
        for (String classesDirectory : pluginDependencyClasspath.getClassesDirectories()) {
            File classesDirectoryFile = pluginDependencyZip.unzippedPath().resolve(classesDirectory).toFile();
            classLoader.addFile(classesDirectoryFile);
        }

        WeakReference<PluginClassLoader> weakRef = new WeakReference<>(classLoader);

        classLoader.loadClass(JavaSources.GREETING_CLASS_NAME);

        classLoader.close();
        classLoader = null;

        System.gc();
        System.runFinalization();
        Thread.sleep(100);

        assertNull(weakRef.get(), "ClassLoader was not garbage collected");
    }

    private PluginClassLoader createPluginClassLoader(String pluginId, String dependencies) throws IOException {
        DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor()
            .setPluginId(pluginId)
            .setPluginVersion("1.2.3")
            .setPluginDescription("My plugin")
            .setDependencies(dependencies)
            .setProvider("Me")
            .setRequires("5.0.0");

        Path pluginPath = pluginsPath.resolve(descriptor.getPluginId() + "-" + descriptor.getVersion() + ".zip");
        PluginZip pluginZip = new PluginZip.Builder(pluginPath, descriptor.getPluginId())
            .pluginVersion(descriptor.getVersion())
            .build();

        pluginZip.unzip();

        PluginClassLoader pluginClassLoader = new PluginClassLoader(pluginManager, descriptor, PluginClassLoaderTest.class.getClassLoader(), ClassLoadingStrategy.PDA);
        pluginManager.addClassLoader(descriptor.getPluginId(), pluginClassLoader);
        addPluginClasspath(pluginClassLoader, pluginZip.unzippedPath());

        return pluginClassLoader;
    }

    private void addPluginClasspath(PluginClassLoader pluginClassLoader, Path pluginRoot) {
        PluginClasspath pluginClasspath = new DefaultPluginClasspath();
        for (String classesDirectory : pluginClasspath.getClassesDirectories()) {
            pluginClassLoader.addFile(pluginRoot.resolve(classesDirectory).toFile());
        }

        for (String jarsDirectory : pluginClasspath.getJarsDirectories()) {
            Path jarsDirectoryPath = pluginRoot.resolve(jarsDirectory);
            List<File> jars = FileUtils.getJars(jarsDirectoryPath);
            for (File jar : jars) {
                pluginClassLoader.addFile(jar);
            }
        }
    }

    private static JavaFileObject createConflictClassSource(String message) {
        return JavaFileObjects.forSourceLines(CONFLICT_CLASS_NAME,
            "package org.pf4j.test;",
            "import org.pf4j.Extension;",
            "@Extension",
            "public class TestExtension implements TestExtensionPoint {",
            "    @Override",
            "    public String saySomething() {",
            "        return \"" + message + "\";",
            "    }",
            "}");
    }

    private static String invokeSaySomething(Class<?> type) throws Exception {
        Object extension = type.getDeclaredConstructor().newInstance();
        return (String) type.getMethod("saySomething").invoke(extension);
    }

    private static void assertFirstLine(String expected, URL resource) throws URISyntaxException, IOException {
        assertNotNull(resource);
        assertEquals(expected, Files.readAllLines(Paths.get(resource.toURI())).get(0));
    }

    private static void assertNumberOfResourcesAndFirstLineOfFirstElement(int expectedCount, String expectedFirstLine, Enumeration<URL> resources) throws URISyntaxException, IOException {
        List<URL> list = Collections.list(resources);
        assertEquals(expectedCount, list.size());

        URL firstResource = list.get(0);
        assertEquals(expectedFirstLine, Files.readAllLines(Paths.get(firstResource.toURI())).get(0));
    }

    @Test
    void shouldDelegateToParentForPf4jCoreClasses() {
        assertTrue(parentLastPluginClassLoader.shouldDelegateToParent("org.pf4j.PluginManager"));
        assertTrue(parentLastPluginClassLoader.shouldDelegateToParent("org.pf4j.PluginClassLoader"));
        assertTrue(parentLastPluginClassLoader.shouldDelegateToParent("org.pf4j.ExtensionPoint"));
    }

    @Test
    void shouldDelegateToParentForDemoClasses() {
        assertTrue(parentLastPluginClassLoader.shouldDelegateToParent("org.pf4j.demo.Boot"));
        assertTrue(parentLastPluginClassLoader.shouldDelegateToParent("org.pf4j.demo.api.Greeting"));
    }

    @Test
    void shouldNotDelegateToParentForTestClasses() {
        assertFalse(parentLastPluginClassLoader.shouldDelegateToParent("org.pf4j.test.TestExtension"));
        assertFalse(parentLastPluginClassLoader.shouldDelegateToParent("org.pf4j.test.PluginJar"));
    }

    @Test
    void shouldNotDelegateToParentForNonPf4jClasses() {
        assertFalse(parentLastPluginClassLoader.shouldDelegateToParent("com.example.MyClass"));
        assertFalse(parentLastPluginClassLoader.shouldDelegateToParent("org.springframework.Boot"));
    }

    @Test
    void shouldDelegateToParentCanBeOverridden() {
        PluginClassLoader customClassLoader = new PluginClassLoader(pluginManager, pluginDescriptor, PluginClassLoaderTest.class.getClassLoader()) {
            @Override
            protected boolean shouldDelegateToParent(String className) {
                return super.shouldDelegateToParent(className)
                    && !className.startsWith("org.pf4j.plus.demo");
            }
        };

        assertFalse(customClassLoader.shouldDelegateToParent("org.pf4j.plus.demo.Boot"));
        assertFalse(customClassLoader.shouldDelegateToParent("org.pf4j.plus.demo.plugins.PluginA"));
        assertTrue(customClassLoader.shouldDelegateToParent("org.pf4j.PluginManager"));
        assertTrue(customClassLoader.shouldDelegateToParent("org.pf4j.Extension"));
        assertFalse(customClassLoader.shouldDelegateToParent("org.pf4j.test.TestExtension"));
    }

    static class TestPluginManager extends DefaultPluginManager {

        public TestPluginManager(Path pluginsPath) {
            super(pluginsPath);
        }

        void addClassLoader(String pluginId, PluginClassLoader classLoader) {
            getPluginClassLoaders().put(pluginId, classLoader);
        }

    }

}

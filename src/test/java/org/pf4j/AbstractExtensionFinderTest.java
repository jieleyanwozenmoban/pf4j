/*
 * Copyright (C) 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pf4j;

import kotlin.sequences.Sequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pf4j.test.JavaFileObjectClassLoader;
import org.pf4j.test.JavaFileObjectUtils;
import org.pf4j.test.JavaSources;
import org.pf4j.test.TestExtension;
import org.pf4j.test.TestExtensionPoint;

import javax.tools.JavaFileObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Mario Franco
 */
class AbstractExtensionFinderTest {

    private PluginManager pluginManager;

    @BeforeEach
    public void setUp() {
        PluginWrapper pluginStarted = mock(PluginWrapper.class);
        when(pluginStarted.getPluginClassLoader()).thenReturn(getClass().getClassLoader());
        when(pluginStarted.getPluginState()).thenReturn(PluginState.STARTED);

        PluginWrapper pluginStopped = mock(PluginWrapper.class);
        when(pluginStopped.getPluginClassLoader()).thenReturn(getClass().getClassLoader());
        when(pluginStopped.getPluginState()).thenReturn(PluginState.STOPPED);

        pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("plugin1")).thenReturn(pluginStarted);
        when(pluginManager.getPlugin("plugin2")).thenReturn(pluginStopped);
        when(pluginManager.getPluginClassLoader("plugin1")).thenReturn(getClass().getClassLoader());
        when(pluginManager.getExtensionFactory()).thenReturn(new DefaultExtensionFactory());
    }

    @AfterEach
    public void tearDown() {
        pluginManager = null;
        System.gc();
    }

    @Test
    void testFindFailType() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };
        List<ExtensionWrapper<TestExtension>> list = instance.find(TestExtension.class);
        assertEquals(0, list.size());
    }

    @Test
    void testFindFromClasspath() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put(null, bucket);

                return entries;
            }

        };

        List<ExtensionWrapper<TestExtensionPoint>> list = instance.find(TestExtensionPoint.class);
        assertEquals(1, list.size());
    }

    @Test
    void testFindFromPlugin() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put("plugin1", bucket);
                bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put("plugin2", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        List<ExtensionWrapper<TestExtensionPoint>> list = instance.find(TestExtensionPoint.class);
        assertEquals(1, list.size());

        list = instance.find(TestExtensionPoint.class, "plugin1");
        assertEquals(1, list.size());

        list = instance.find(TestExtensionPoint.class, "plugin2");
        assertEquals(0, list.size());
    }

    @Test
    void testFindClassNames() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.plugin.TestExtension");
                entries.put("plugin1", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.plugin.TestExtension");
                bucket.add("org.pf4j.plugin.FailTestExtension");
                entries.put(null, bucket);

                return entries;
            }

        };

        Set<String> result = instance.findClassNames(null);
        assertEquals(2, result.size());

        result = instance.findClassNames("plugin1");
        assertEquals(1, result.size());
    }

    @Test
    void testFindClassNamesWithFilterUsesDedicatedCache() {
        AtomicInteger filteredEntriesCreated = new AtomicInteger();
        AbstractExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();
                entries.put("plugin1", linkedSet(TestExtension.class.getName(), SpecializedTestExtension.class.getName()));
                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

            @Override
            protected Map<String, Set<String>> createFilteredEntries(ExtensionFilter filter) {
                filteredEntriesCreated.incrementAndGet();
                return super.createFilteredEntries(filter);
            }

        };

        ExtensionFilter filter = ExtensionFilter.builder()
            .pluginId("plugin1")
            .extensionClassName(SpecializedTestExtension.class.getName())
            .build();

        Set<String> first = instance.findClassNames("plugin1", filter);
        Set<String> second = instance.findClassNames("plugin1", filter);
        Set<String> unfiltered = instance.findClassNames("plugin1");

        assertEquals(Collections.singleton(SpecializedTestExtension.class.getName()), first);
        assertEquals(first, second);
        assertEquals(1, filteredEntriesCreated.get());
        assertEquals(2, unfiltered.size());
        assertTrue(unfiltered.contains(TestExtension.class.getName()));
        assertTrue(unfiltered.contains(SpecializedTestExtension.class.getName()));
    }

    @Test
    void testFindWithCombinedFilters() {
        ExtensionFinder instance = createFilterableFinder();
        ExtensionFilter filter = ExtensionFilter.builder()
            .pluginId("plugin1")
            .extensionClassName(SpecializedTestExtension.class.getName())
            .extensionType(TestExtension.class)
            .build();

        List<ExtensionWrapper<TestExtensionPoint>> list = instance.find(TestExtensionPoint.class, filter);

        assertEquals(1, list.size());
        assertSame(SpecializedTestExtension.class, list.get(0).getDescriptor().extensionClass);
    }

    @Test
    void testFindWithFilterNoResult() {
        ExtensionFinder instance = createFilterableFinder();
        ExtensionFilter filter = ExtensionFilter.builder()
            .pluginId("plugin1")
            .extensionClassName(TestExtension.class.getName())
            .extensionType(SpecializedTestExtension.class)
            .build();

        assertTrue(instance.find(TestExtensionPoint.class, filter).isEmpty());
        assertTrue(instance.find("plugin1", filter).isEmpty());
    }

    @Test
    void testFindExtensionWrappersFromPluginId() {
        PluginWrapper plugin3 = mock(PluginWrapper.class);
        JavaFileObject object = JavaSources.compile(DefaultExtensionFactoryTest.FailTestExtension);
        JavaFileObjectClassLoader classLoader = new JavaFileObjectClassLoader();
        classLoader.load(object);
        when(plugin3.getPluginClassLoader()).thenReturn(classLoader);
        when(plugin3.getPluginState()).thenReturn(PluginState.STARTED);
        when(pluginManager.getPluginClassLoader("plugin3")).thenReturn(classLoader);
        when(pluginManager.getPlugin("plugin3")).thenReturn(plugin3);

        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put("plugin1", bucket);
                bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put("plugin2", bucket);
                bucket = new HashSet<>();
                bucket.add(JavaFileObjectUtils.getClassName(object));
                entries.put("plugin3", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        List<ExtensionWrapper> plugin1Result = instance.find("plugin1");
        assertEquals(1, plugin1Result.size());

        List<ExtensionWrapper> plugin2Result = instance.find("plugin2");
        assertEquals(0, plugin2Result.size());

        List<ExtensionWrapper> plugin3Result = instance.find("plugin3");
        assertEquals(1, plugin3Result.size());

        List<ExtensionWrapper> plugin4Result = instance.find(UUID.randomUUID().toString());
        assertEquals(0, plugin4Result.size());
    }

    @Test
    void findExtensionAnnotation() {
        List<JavaFileObject> generatedFiles = JavaSources.compileAll(JavaSources.GREETING, JavaSources.WHAZZUP_GREETING);
        assertEquals(2, generatedFiles.size());

        Map<String, Class<?>> loadedClasses = new JavaFileObjectClassLoader().load(generatedFiles);
        Class<?> clazz = loadedClasses.get(JavaSources.WHAZZUP_GREETING_CLASS_NAME);
        Extension extension = AbstractExtensionFinder.findExtensionAnnotation(clazz);
        Assertions.assertNotNull(extension);
    }

    @Test
    void findExtensionAnnotationThatMissing() {
        List<JavaFileObject> generatedFiles = JavaSources.compileAll(JavaSources.GREETING,
            ExtensionAnnotationProcessorTest.SpinnakerExtension_NoExtension,
            ExtensionAnnotationProcessorTest.WhazzupGreeting_SpinnakerExtension);
        assertEquals(3, generatedFiles.size());

        Map<String, Class<?>> loadedClasses = new JavaFileObjectClassLoader().load(generatedFiles);
        Class<?> clazz = loadedClasses.get(JavaSources.WHAZZUP_GREETING_CLASS_NAME);
        Extension extension = AbstractExtensionFinder.findExtensionAnnotation(clazz);
        Assertions.assertNull(extension);
    }

    @Test
    public void runningOnNonExtensionKotlinClassDoesNotThrowException() {
        Extension result = AbstractExtensionFinder.findExtensionAnnotation(Sequence.class);

        Assertions.assertNull(result);
    }

    @Test
    void checkDifferentClassLoaders() {
        AbstractExtensionFinder extensionFinder = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        List<JavaFileObject> generatedFiles = JavaSources.compileAll(JavaSources.GREETING, JavaSources.WHAZZUP_GREETING);
        assertEquals(2, generatedFiles.size());
        Class<?> extensionPointClass = new JavaFileObjectClassLoader().load(generatedFiles).get(JavaSources.GREETING_CLASS_NAME);
        Class<?> extensionClass = new JavaFileObjectClassLoader().load(generatedFiles).get(JavaSources.WHAZZUP_GREETING_CLASS_NAME);

        assertTrue(extensionFinder.checkDifferentClassLoaders(extensionPointClass, extensionClass));
    }

    private ExtensionFinder createFilterableFinder() {
        return new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();
                entries.put("plugin1", linkedSet(TestExtension.class.getName(), SpecializedTestExtension.class.getName()));
                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };
    }

    private static Set<String> linkedSet(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    @Extension
    public static class SpecializedTestExtension extends TestExtension {
    }

}

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pf4j.test.TestExtension;
import org.pf4j.test.TestExtensionPoint;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtensionFilterTest {

    private PluginManager pluginManager;

    @BeforeEach
    public void setUp() {
        PluginWrapper pluginStarted = mock(PluginWrapper.class);
        when(pluginStarted.getPluginClassLoader()).thenReturn(getClass().getClassLoader());
        when(pluginStarted.getPluginState()).thenReturn(PluginState.STARTED);

        pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("plugin1")).thenReturn(pluginStarted);
        when(pluginManager.getPlugin("plugin2")).thenReturn(pluginStarted);
        when(pluginManager.getPluginClassLoader("plugin1")).thenReturn(getClass().getClassLoader());
        when(pluginManager.getPluginClassLoader("plugin2")).thenReturn(getClass().getClassLoader());
        when(pluginManager.getExtensionFactory()).thenReturn(new DefaultExtensionFactory());
    }

    @AfterEach
    public void tearDown() {
        pluginManager = null;
        System.gc();
    }

    @Test
    void testFindWithNullFilterReturnsAllExtensions() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put("plugin1", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        List<ExtensionWrapper<TestExtensionPoint>> resultNull = instance.find(TestExtensionPoint.class, (ExtensionFilter) null);
        List<ExtensionWrapper<TestExtensionPoint>> resultAll = instance.find(TestExtensionPoint.class, ExtensionFilter.acceptingAll());

        assertEquals(1, resultNull.size());
        assertEquals(1, resultAll.size());
    }

    @Test
    void testFindWithPluginIdFilter() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket1 = new HashSet<>();
                bucket1.add("org.pf4j.test.TestExtension");
                entries.put("plugin1", bucket1);

                Set<String> bucket2 = new HashSet<>();
                bucket2.add("org.pf4j.test.TestExtension");
                entries.put("plugin2", bucket2);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        ExtensionFilter plugin1Filter = ExtensionFilter.pluginId("plugin1");
        List<ExtensionWrapper<TestExtensionPoint>> result = instance.find(TestExtensionPoint.class, "plugin1", plugin1Filter);
        assertEquals(1, result.size());

        ExtensionFilter nonExistentFilter = ExtensionFilter.pluginId("non-existent");
        List<ExtensionWrapper<TestExtensionPoint>> emptyResult = instance.find(TestExtensionPoint.class, nonExistentFilter);
        assertEquals(0, emptyResult.size());
    }

    @Test
    void testFindWithClassNameFilter() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                bucket.add("org.pf4j.test.FailingExtension");
                entries.put("plugin1", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        ExtensionFilter classNameFilter = ExtensionFilter.className("org.pf4j.test.TestExtension");
        List<ExtensionWrapper<TestExtensionPoint>> result = instance.find(TestExtensionPoint.class, classNameFilter);
        assertEquals(1, result.size());

        ExtensionFilter nonExistentFilter = ExtensionFilter.className("org.pf4j.test.NonExistentExtension");
        List<ExtensionWrapper<TestExtensionPoint>> emptyResult = instance.find(TestExtensionPoint.class, nonExistentFilter);
        assertEquals(0, emptyResult.size());
    }

    @Test
    void testFindWithCombinedFilter() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket1 = new HashSet<>();
                bucket1.add("org.pf4j.test.TestExtension");
                entries.put("plugin1", bucket1);

                Set<String> bucket2 = new HashSet<>();
                bucket2.add("org.pf4j.test.TestExtension");
                entries.put("plugin2", bucket2);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        ExtensionFilter combinedFilter = ExtensionFilter.of("plugin1", "org.pf4j.test.TestExtension", null);
        List<ExtensionWrapper<TestExtensionPoint>> result = instance.find(TestExtensionPoint.class, combinedFilter);
        assertEquals(1, result.size());

        ExtensionFilter wrongPluginFilter = ExtensionFilter.of("plugin2", "org.pf4j.test.TestExtension", null);
        List<ExtensionWrapper<TestExtensionPoint>> wrongPluginResult = instance.find(TestExtensionPoint.class, wrongPluginFilter);
        assertEquals(0, wrongPluginResult.size());
    }

    @Test
    void testFindWithNoResults() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put("plugin1", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        ExtensionFilter nonMatchingFilter = info -> false;
        List<ExtensionWrapper<TestExtensionPoint>> emptyResult = instance.find(TestExtensionPoint.class, nonMatchingFilter);
        assertEquals(0, emptyResult.size());

        List<ExtensionWrapper<TestExtensionPoint>> emptyResultForPlugin = instance.find(TestExtensionPoint.class, "plugin1", nonMatchingFilter);
        assertEquals(0, emptyResultForPlugin.size());
    }

    @Test
    void testCacheNotPollutedByFilters() {
        AbstractExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put("plugin1", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        ExtensionFilter filter1 = ExtensionFilter.className("org.pf4j.test.TestExtension");
        List<ExtensionWrapper<TestExtensionPoint>> filteredResult = instance.find(TestExtensionPoint.class, filter1);
        assertEquals(1, filteredResult.size());

        List<ExtensionWrapper<TestExtensionPoint>> unfilteredResult = instance.find(TestExtensionPoint.class);
        assertEquals(1, unfilteredResult.size());

        ExtensionFilter filter2 = ExtensionFilter.className("non.existent.Class");
        List<ExtensionWrapper<TestExtensionPoint>> emptyFilterResult = instance.find(TestExtensionPoint.class, filter2);
        assertEquals(0, emptyFilterResult.size());

        List<ExtensionWrapper<TestExtensionPoint>> unfilteredResult2 = instance.find(TestExtensionPoint.class);
        assertEquals(1, unfilteredResult2.size());
    }

    @Test
    void testFindByPluginIdWithFilter() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                entries.put("plugin1", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        List<ExtensionWrapper> allExtensions = instance.find("plugin1");
        assertEquals(1, allExtensions.size());

        ExtensionFilter classNameFilter = ExtensionFilter.className("org.pf4j.test.TestExtension");
        List<ExtensionWrapper> filteredExtensions = instance.find("plugin1", classNameFilter);
        assertEquals(1, filteredExtensions.size());

        ExtensionFilter nonMatchingFilter = ExtensionFilter.className("org.pf4j.test.NonExistent");
        List<ExtensionWrapper> emptyFilteredExtensions = instance.find("plugin1", nonMatchingFilter);
        assertEquals(0, emptyFilteredExtensions.size());
    }

    @Test
    void testFilterWithClassNamePattern() {
        ExtensionFinder instance = new AbstractExtensionFinder(pluginManager) {

            @Override
            public Map<String, Set<String>> readPluginsStorages() {
                Map<String, Set<String>> entries = new LinkedHashMap<>();

                Set<String> bucket = new HashSet<>();
                bucket.add("org.pf4j.test.TestExtension");
                bucket.add("org.pf4j.test.FailingExtension");
                entries.put("plugin1", bucket);

                return entries;
            }

            @Override
            public Map<String, Set<String>> readClasspathStorages() {
                return Collections.emptyMap();
            }

        };

        ExtensionFilter patternFilter = ExtensionFilter.classNamePattern("org\\.pf4j\\.test\\.Test.*");
        List<ExtensionWrapper<TestExtensionPoint>> result = instance.find(TestExtensionPoint.class, patternFilter);
        assertEquals(1, result.size());

        ExtensionFilter failingPatternFilter = ExtensionFilter.classNamePattern("org\\.pf4j\\.test\\.Fail.*");
        List<ExtensionWrapper<TestExtensionPoint>> failingResult = instance.find(TestExtensionPoint.class, failingPatternFilter);
        assertEquals(0, failingResult.size());
    }

}
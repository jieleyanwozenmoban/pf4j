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

import org.pf4j.asm.ExtensionInfo;
import org.pf4j.util.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Decebal Suiu
 */
public abstract class AbstractExtensionFinder implements ExtensionFinder, PluginStateListener {

    private static final Logger log = LoggerFactory.getLogger(AbstractExtensionFinder.class);

    protected PluginManager pluginManager;
    protected volatile Map<String, Set<String>> entries;
    protected volatile Map<ExtensionFilter, Map<String, Set<String>>> filteredEntries;
    protected volatile Map<String, ExtensionInfo> extensionInfos;
    protected Boolean checkForExtensionDependencies = null;

    protected AbstractExtensionFinder(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public abstract Map<String, Set<String>> readPluginsStorages();

    public abstract Map<String, Set<String>> readClasspathStorages();

    @Override
    public <T> List<ExtensionWrapper<T>> find(Class<T> type) {
        return find(type, ExtensionFilter.empty());
    }

    @Override
    public <T> List<ExtensionWrapper<T>> find(Class<T> type, ExtensionFilter filter) {
        ExtensionFilter effectiveFilter = normalizeFilter(filter);
        log.debug("Finding extensions of extension point '{}' using filter '{}'", type.getName(), effectiveFilter);
        Map<String, Set<String>> entries = getEntries(effectiveFilter);
        List<ExtensionWrapper<T>> result = new ArrayList<>();

        for (String pluginId : entries.keySet()) {
            result.addAll(find(type, pluginId, effectiveFilter));
        }

        if (result.isEmpty()) {
            log.debug("No extensions found for extension point '{}'", type.getName());
        } else {
            log.debug("Found {} extensions for extension point '{}'", result.size(), type.getName());
        }

        Collections.sort(result);

        return result;
    }

    @Override
    public <T> List<ExtensionWrapper<T>> find(Class<T> type, String pluginId) {
        return find(type, pluginId, ExtensionFilter.empty());
    }

    @SuppressWarnings("unchecked")
    protected <T> List<ExtensionWrapper<T>> find(Class<T> type, String pluginId, ExtensionFilter filter) {
        ExtensionFilter effectiveFilter = normalizeFilter(filter);
        log.debug("Finding extensions of extension point '{}' for plugin '{}' using filter '{}'", type.getName(), pluginId, effectiveFilter);
        List<ExtensionWrapper<T>> result = new ArrayList<>();

        if (!effectiveFilter.matches(pluginId)) {
            return result;
        }

        Set<String> classNames = findClassNames(pluginId, effectiveFilter);
        if (classNames.isEmpty()) {
            return result;
        }

        if (pluginId != null) {
            PluginWrapper pluginWrapper = pluginManager.getPlugin(pluginId);
            if (!pluginWrapper.getPluginState().isStarted()) {
                return result;
            }

            log.trace("Checking extensions from plugin '{}'", pluginId);
        } else {
            log.trace("Checking extensions from classpath");
        }

        ClassLoader classLoader = getClassLoader(pluginId);

        for (String className : classNames) {
            try {
                if (isCheckForExtensionDependencies()) {
                    ExtensionInfo extensionInfo = getExtensionInfo(className, classLoader);
                    if (extensionInfo == null) {
                        log.error("No extension annotation was found for '{}'", className);
                        continue;
                    }

                    List<String> missingPluginIds = new ArrayList<>();
                    for (String requiredPluginId : extensionInfo.getPlugins()) {
                        PluginWrapper requiredPlugin = pluginManager.getPlugin(requiredPluginId);
                        if (requiredPlugin == null || !requiredPlugin.getPluginState().isStarted()) {
                            missingPluginIds.add(requiredPluginId);
                        }
                    }
                    if (!missingPluginIds.isEmpty()) {
                        StringBuilder missing = new StringBuilder();
                        for (String missingPluginId : missingPluginIds) {
                            if (missing.length() > 0) {
                                missing.append(", ");
                            }
                            missing.append(missingPluginId);
                        }
                        log.trace("Extension '{}' is ignored due to missing plugins: {}", className, missing);
                        continue;
                    }
                }

                log.debug("Loading class '{}' using class loader '{}'", className, classLoader);
                Class<?> extensionClass = classLoader.loadClass(className);

                if (!effectiveFilter.matches(pluginId, className, extensionClass)) {
                    log.trace("'{}' is ignored by filter '{}'", className, effectiveFilter);
                    continue;
                }

                log.debug("Checking extension type '{}'", className);
                if (type.isAssignableFrom(extensionClass)) {
                    ExtensionWrapper extensionWrapper = createExtensionWrapper(extensionClass);
                    result.add(extensionWrapper);
                    log.debug("Added extension '{}' with ordinal {}", className, extensionWrapper.getOrdinal());
                } else {
                    log.trace("'{}' is not an extension for extension point '{}'", className, type.getName());
                    if (checkDifferentClassLoaders(type, extensionClass)) {
                        log.error("Different class loaders: '{}' (E) and '{}' (EP)", extensionClass.getClassLoader(), type.getClassLoader());
                    }
                }
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                log.error(e.getMessage(), e);
            }
        }

        if (result.isEmpty()) {
            log.debug("No extensions found for extension point '{}'", type.getName());
        } else {
            log.debug("Found {} extensions for extension point '{}'", result.size(), type.getName());
        }

        Collections.sort(result);

        return result;
    }

    @Override
    public List<ExtensionWrapper> find(String pluginId) {
        return find(pluginId, ExtensionFilter.empty());
    }

    @Override
    public List<ExtensionWrapper> find(String pluginId, ExtensionFilter filter) {
        ExtensionFilter effectiveFilter = normalizeFilter(filter);
        log.debug("Finding extensions from plugin '{}' using filter '{}'", pluginId, effectiveFilter);
        List<ExtensionWrapper> result = new ArrayList<>();

        if (!effectiveFilter.matches(pluginId)) {
            return result;
        }

        Set<String> classNames = findClassNames(pluginId, effectiveFilter);
        if (classNames.isEmpty()) {
            return result;
        }

        if (pluginId != null) {
            PluginWrapper pluginWrapper = pluginManager.getPlugin(pluginId);
            if (!pluginWrapper.getPluginState().isStarted()) {
                return result;
            }

            log.trace("Checking extensions from plugin '{}'", pluginId);
        } else {
            log.trace("Checking extensions from classpath");
        }

        ClassLoader classLoader = getClassLoader(pluginId);

        for (String className : classNames) {
            try {
                log.debug("Loading class '{}' using class loader '{}'", className, classLoader);
                Class<?> extensionClass = classLoader.loadClass(className);

                if (!effectiveFilter.matches(pluginId, className, extensionClass)) {
                    log.trace("'{}' is ignored by filter '{}'", className, effectiveFilter);
                    continue;
                }

                ExtensionWrapper extensionWrapper = createExtensionWrapper(extensionClass);
                result.add(extensionWrapper);
                log.debug("Added extension '{}' with ordinal {}", className, extensionWrapper.getOrdinal());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                log.error(e.getMessage(), e);
            }
        }

        if (result.isEmpty()) {
            log.debug("No extensions found for plugin '{}'", pluginId);
        } else {
            log.debug("Found {} extensions for plugin '{}'", result.size(), pluginId);
        }

        Collections.sort(result);

        return result;
    }

    @Override
    public Set<String> findClassNames(String pluginId) {
        return findClassNames(pluginId, ExtensionFilter.empty());
    }

    @Override
    public Set<String> findClassNames(String pluginId, ExtensionFilter filter) {
        ExtensionFilter effectiveFilter = normalizeFilter(filter);
        if (!effectiveFilter.matches(pluginId)) {
            return Collections.emptySet();
        }

        Set<String> classNames = getEntries(effectiveFilter).get(pluginId);
        if (classNames == null) {
            return Collections.emptySet();
        }

        return classNames;
    }

    @Override
    public void pluginStateChanged(PluginStateEvent event) {
        entries = null;
        filteredEntries = null;

        if (checkForExtensionDependencies == null && event.getPluginState().isStarted()) {
            for (PluginDependency dependency : event.getPlugin().getDescriptor().getDependencies()) {
                if (dependency.isOptional()) {
                    log.debug("Enable check for extension dependencies via ASM.");
                    checkForExtensionDependencies = true;
                    break;
                }
            }
        }
    }

    public final boolean isCheckForExtensionDependencies() {
        return Boolean.TRUE.equals(checkForExtensionDependencies);
    }

    public void setCheckForExtensionDependencies(boolean checkForExtensionDependencies) {
        this.checkForExtensionDependencies = checkForExtensionDependencies;
    }

    protected void debugExtensions(Set<String> extensions) {
        if (log.isDebugEnabled()) {
            if (extensions.isEmpty()) {
                log.debug("No extensions found");
            } else {
                log.debug("Found possible {} extensions:", extensions.size());
                for (String extension : extensions) {
                    log.debug("   " + extension);
                }
            }
        }
    }

    protected Map<String, Set<String>> createFilteredEntries(ExtensionFilter filter) {
        Map<String, Set<String>> sourceEntries = getEntries();
        Map<String, Set<String>> result = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : sourceEntries.entrySet()) {
            String pluginId = entry.getKey();
            if (!filter.matches(pluginId)) {
                continue;
            }

            Set<String> filteredClassNames = new LinkedHashSet<>();
            for (String className : entry.getValue()) {
                if (matchesFilter(pluginId, className, filter)) {
                    filteredClassNames.add(className);
                }
            }

            if (!filteredClassNames.isEmpty()) {
                result.put(pluginId, filteredClassNames);
            }
        }

        return result;
    }

    private boolean matchesFilter(String pluginId, String className, ExtensionFilter filter) {
        if (!filter.matches(pluginId, className)) {
            return false;
        }

        if (!filter.hasExtensionType()) {
            return true;
        }

        try {
            Class<?> extensionClass = getClassLoader(pluginId).loadClass(className);
            return filter.matches(pluginId, className, extensionClass);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private Map<String, Set<String>> readStorages() {
        Map<String, Set<String>> result = new LinkedHashMap<>();

        result.putAll(readClasspathStorages());
        result.putAll(readPluginsStorages());

        return result;
    }

    private Map<String, Set<String>> getEntries() {
        if (entries == null) {
            entries = readStorages();
        }

        return entries;
    }

    private Map<String, Set<String>> getEntries(ExtensionFilter filter) {
        ExtensionFilter effectiveFilter = normalizeFilter(filter);
        if (effectiveFilter.isEmpty()) {
            return getEntries();
        }

        if (filteredEntries == null) {
            filteredEntries = new HashMap<>();
        }

        if (!filteredEntries.containsKey(effectiveFilter)) {
            filteredEntries.put(effectiveFilter, createFilteredEntries(effectiveFilter));
        }

        return filteredEntries.get(effectiveFilter);
    }

    private ExtensionFilter normalizeFilter(ExtensionFilter filter) {
        return filter != null ? filter : ExtensionFilter.empty();
    }

    private ClassLoader getClassLoader(String pluginId) {
        return pluginId != null ? pluginManager.getPluginClassLoader(pluginId) : getClass().getClassLoader();
    }

    private ExtensionInfo getExtensionInfo(String className, ClassLoader classLoader) {
        if (extensionInfos == null) {
            extensionInfos = new HashMap<>();
        }

        if (!extensionInfos.containsKey(className)) {
            log.trace("Load annotation for '{}' using asm", className);
            ExtensionInfo info = ExtensionInfo.load(className, classLoader);
            if (info == null) {
                log.warn("No extension annotation was found for '{}'", className);
                extensionInfos.put(className, null);
            } else {
                extensionInfos.put(className, info);
            }
        }

        return extensionInfos.get(className);
    }

    private ExtensionWrapper createExtensionWrapper(Class<?> extensionClass) {
        Extension extensionAnnotation = findExtensionAnnotation(extensionClass);
        int ordinal = extensionAnnotation != null ? extensionAnnotation.ordinal() : 0;
        ExtensionDescriptor descriptor = new ExtensionDescriptor(ordinal, extensionClass);

        return new ExtensionWrapper<>(descriptor, pluginManager.getExtensionFactory());
    }

    public static Extension findExtensionAnnotation(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Extension.class)) {
            return clazz.getAnnotation(Extension.class);
        }

        for (Annotation annotation : clazz.getAnnotations()) {
            Class<? extends Annotation> annotationClass = annotation.annotationType();
            if (!annotationClass.getName().startsWith("java.lang.annotation") && !annotationClass.getName().startsWith("kotlin")) {
                if (annotationClass.equals(clazz)) {
                    continue;
                }

                Extension extensionAnnotation = findExtensionAnnotation(annotationClass);
                if (extensionAnnotation != null) {
                    return extensionAnnotation;
                }
            }
        }

        return null;
    }

    boolean checkDifferentClassLoaders(Class<?> type, Class<?> extensionClass) {
        ClassLoader typeClassLoader = type.getClassLoader();
        ClassLoader extensionClassLoader = extensionClass.getClassLoader();
        boolean match = ClassUtils.getAllInterfacesNames(extensionClass).contains(type.getSimpleName());
        return match && extensionClassLoader != typeClassLoader;
    }

}

package com.example.litepf4j.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ExtensionRegistry {
    private static final Comparator<RegisteredExtension<?>> BY_ORDINAL_DESC = Comparator
            .comparingInt((RegisteredExtension<?> registered) -> registered.ordinal())
            .reversed();

    private final Map<Class<? extends ExtensionPoint>, Map<String, List<RegisteredExtension<?>>>> extensions = new ConcurrentHashMap<>();

    public <T extends ExtensionPoint> void register(String pluginId, Class<T> extensionType, T instance, int ordinal) {
        extensions.computeIfAbsent(extensionType, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(pluginId, ignored -> new ArrayList<>())
                .add(new RegisteredExtension<>(pluginId, extensionType, instance, ordinal));
    }

    public <T extends ExtensionPoint> List<T> getExtensions(Class<T> extensionType) {
        Map<String, List<RegisteredExtension<?>>> byPlugin = extensions.getOrDefault(extensionType, Map.of());
        return byPlugin.values().stream()
                .flatMap(List::stream)
                .sorted(BY_ORDINAL_DESC)
                .map(registered -> extensionType.cast(registered.instance()))
                .toList();
    }

    public <T extends ExtensionPoint> List<T> getExtensions(Class<T> extensionType, String pluginId) {
        Map<String, List<RegisteredExtension<?>>> byPlugin = extensions.getOrDefault(extensionType, Map.of());
        return byPlugin.getOrDefault(pluginId, List.of()).stream()
                .sorted(BY_ORDINAL_DESC)
                .map(registered -> extensionType.cast(registered.instance()))
                .toList();
    }

    public <T extends ExtensionPoint> Map<String, List<T>> getExtensionsByPlugin(Class<T> extensionType) {
        Map<String, List<RegisteredExtension<?>>> byPlugin = extensions.getOrDefault(extensionType, Map.of());
        Map<String, List<T>> result = new LinkedHashMap<>();
        byPlugin.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(BY_ORDINAL_DESC)
                                .map(registered -> extensionType.cast(registered.instance()))
                                .collect(Collectors.toList())
                ));
        return result;
    }

    public Map<String, Map<Class<? extends ExtensionPoint>, List<ExtensionPoint>>> snapshotByPlugin() {
        Map<String, Map<Class<? extends ExtensionPoint>, List<ExtensionPoint>>> snapshot = new LinkedHashMap<>();
        extensions.forEach((extensionType, byPlugin) -> byPlugin.forEach((pluginId, registrations) -> snapshot
                .computeIfAbsent(pluginId, ignored -> new LinkedHashMap<>())
                .put(extensionType, registrations.stream()
                        .sorted(BY_ORDINAL_DESC)
                        .map(registered -> (ExtensionPoint) registered.instance())
                        .toList())));
        return snapshot;
    }

    private record RegisteredExtension<T extends ExtensionPoint>(
            String pluginId,
            Class<T> extensionType,
            T instance,
            int ordinal
    ) {
    }
}

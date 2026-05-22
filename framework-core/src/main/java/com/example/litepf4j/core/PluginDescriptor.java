package com.example.litepf4j.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PluginDescriptor(
        String pluginId,
        String version,
        String provider,
        String description,
        String pluginClass,
        Map<String, String> properties
) {
    public PluginDescriptor {
        Objects.requireNonNull(pluginId, "pluginId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}

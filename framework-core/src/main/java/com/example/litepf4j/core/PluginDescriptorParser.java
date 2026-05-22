package com.example.litepf4j.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class PluginDescriptorParser {
    public PluginDescriptor parse(Path pluginPropertiesPath) throws IOException {
        if (!Files.exists(pluginPropertiesPath)) {
            throw new IllegalArgumentException("Missing plugin.properties: " + pluginPropertiesPath);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(pluginPropertiesPath)) {
            properties.load(inputStream);
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
        }

        String pluginId = requireProperty(values, "plugin.id");
        String version = requireProperty(values, "plugin.version");

        return new PluginDescriptor(
                pluginId,
                version,
                values.getOrDefault("plugin.provider", "unknown"),
                values.getOrDefault("plugin.description", ""),
                values.getOrDefault("plugin.class", ""),
                values
        );
    }

    private String requireProperty(Map<String, String> properties, String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property '" + key + "'");
        }
        return value.trim();
    }
}

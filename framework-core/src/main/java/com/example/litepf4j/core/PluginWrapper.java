package com.example.litepf4j.core;

import java.nio.file.Path;

public record PluginWrapper(
        PluginDescriptor descriptor,
        Path archivePath,
        Path extractedPath,
        Path classesPath,
        PluginClassLoader classLoader
) {
}

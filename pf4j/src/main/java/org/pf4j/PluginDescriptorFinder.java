package org.pf4j;

import java.nio.file.Path;

/**
 * Plugin descriptor finder interface
 */
public interface PluginDescriptorFinder {
    PluginDescriptor find(Path pluginPath);
}

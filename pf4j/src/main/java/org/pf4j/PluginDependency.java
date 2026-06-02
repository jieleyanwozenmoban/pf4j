package org.pf4j;

/**
 * Represents a plugin dependency
 */
public class PluginDependency {
    private final String pluginId;
    private final String pluginVersionSupport;
    private final boolean optional;

    public PluginDependency(String dependencyString) {
        String[] parts = dependencyString.split("@");
        this.pluginId = parts[0].trim();
        this.pluginVersionSupport = parts.length > 1 ? parts[1].trim() : "*";
        this.optional = false;
    }

    public PluginDependency(String pluginId, String pluginVersionSupport, boolean optional) {
        this.pluginId = pluginId;
        this.pluginVersionSupport = pluginVersionSupport;
        this.optional = optional;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getPluginVersionSupport() {
        return pluginVersionSupport;
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    public String toString() {
        return pluginId + "@" + pluginVersionSupport + (optional ? " (optional)" : "");
    }
}

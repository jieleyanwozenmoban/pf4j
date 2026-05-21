package org.pf4j;

/**
 * Filter used to restrict the extensions returned by {@link ExtensionFinder}.
 *
 * @author pf4j
 */
public class ExtensionFilter {

    private String pluginId;
    private String extensionClassName;
    private Class<?> extensionType;

    public ExtensionFilter() {
    }

    public ExtensionFilter pluginId(String pluginId) {
        this.pluginId = pluginId;
        return this;
    }

    public ExtensionFilter extensionClassName(String extensionClassName) {
        this.extensionClassName = extensionClassName;
        return this;
    }

    public ExtensionFilter extensionType(Class<?> extensionType) {
        this.extensionType = extensionType;
        return this;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getExtensionClassName() {
        return extensionClassName;
    }

    public Class<?> getExtensionType() {
        return extensionType;
    }
}

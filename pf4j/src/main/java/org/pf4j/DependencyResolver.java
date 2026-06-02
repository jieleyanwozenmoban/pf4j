package org.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Resolves plugin dependencies
 */
public class DependencyResolver {
    private static final Logger log = LoggerFactory.getLogger(DependencyResolver.class);

    private final List<PluginDescriptor> plugins = new ArrayList<>();
    private final Map<String, List<String>> dependencies = new LinkedHashMap<>();

    public DependencyResolver() {
    }

    /**
     * Add a plugin descriptor to the resolver
     */
    public void addPluginDescriptor(PluginDescriptor descriptor) {
        plugins.add(descriptor);
    }

    /**
     * Resolve dependencies for all plugins
     */
    public void resolve() {
        dependencies.clear();

        for (PluginDescriptor descriptor : plugins) {
            String pluginId = descriptor.getPluginId();
            List<String> deps = parseDependencies(descriptor.getDependencies());
            dependencies.put(pluginId, deps);
        }

        // Check for circular dependencies
        checkForCircularDependencies();
    }

    /**
     * Get the list of plugin IDs that depend on the given plugin
     * This is the key method for dependency protection during unload
     */
    public List<String> getDependents(String pluginId) {
        List<String> dependents = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            String dependentPluginId = entry.getKey();
            List<String> deps = entry.getValue();

            if (deps.contains(pluginId)) {
                dependents.add(dependentPluginId);
            }
        }

        return dependents;
    }

    /**
     * Get all transitive dependents (plugins that directly or indirectly depend on the given plugin)
     */
    public List<String> getTransitiveDependents(String pluginId) {
        List<String> allDependents = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        collectTransitiveDependents(pluginId, allDependents, visited);
        return allDependents;
    }

    private void collectTransitiveDependents(String pluginId, List<String> allDependents, Set<String> visited) {
        List<String> directDependents = getDependents(pluginId);

        for (String dependent : directDependents) {
            if (!visited.contains(dependent)) {
                visited.add(dependent);
                allDependents.add(dependent);
                collectTransitiveDependents(dependent, allDependents, visited);
            }
        }
    }

    /**
     * Get the dependencies of a specific plugin
     */
    public List<String> getDependencies(String pluginId) {
        return dependencies.getOrDefault(pluginId, Collections.emptyList());
    }

    /**
     * Check if a plugin can be safely unloaded (no other plugins depend on it)
     */
    public boolean canUnload(String pluginId) {
        return getDependents(pluginId).isEmpty();
    }

    /**
     * Get the dependency graph as a string for debugging
     */
    public String getDependenciesGraph() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dependencies graph:\n");

        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Parse dependencies from the plugin descriptor
     */
    private List<String> parseDependencies(String dependenciesString) {
        List<String> deps = new ArrayList<>();

        if (dependenciesString == null || dependenciesString.trim().isEmpty()) {
            return deps;
        }

        String[] parts = dependenciesString.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                // Extract plugin ID from "pluginId@version" format
                String pluginId = trimmed.split("@")[0].trim();
                deps.add(pluginId);
            }
        }

        return deps;
    }

    /**
     * Check for circular dependencies
     */
    private void checkForCircularDependencies() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String pluginId : dependencies.keySet()) {
            if (!visited.contains(pluginId)) {
                if (hasCycle(pluginId, visited, recursionStack)) {
                    throw new PluginRuntimeException("Circular dependency detected involving plugin: " + pluginId);
                }
            }
        }
    }

    private boolean hasCycle(String pluginId, Set<String> visited, Set<String> recursionStack) {
        visited.add(pluginId);
        recursionStack.add(pluginId);

        for (String dep : dependencies.getOrDefault(pluginId, Collections.emptyList())) {
            if (!visited.contains(dep)) {
                if (hasCycle(dep, visited, recursionStack)) {
                    return true;
                }
            } else if (recursionStack.contains(dep)) {
                return true;
            }
        }

        recursionStack.remove(pluginId);
        return false;
    }
}

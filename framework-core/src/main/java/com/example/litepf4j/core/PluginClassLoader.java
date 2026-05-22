package com.example.litepf4j.core;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class PluginClassLoader extends URLClassLoader {
    private final String pluginId;

    public PluginClassLoader(String pluginId, Path classesPath, ClassLoader parent) {
        super(new URL[]{toUrl(classesPath)}, parent);
        this.pluginId = pluginId;
    }

    public String getPluginId() {
        return pluginId;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                if (isParentFirst(name)) {
                    loadedClass = getParent().loadClass(name);
                } else {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = getParent().loadClass(name);
                    }
                }
            }

            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }

    private boolean isParentFirst(String className) {
        return className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("com.example.litepf4j.core");
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot convert path to URL: " + path, exception);
        }
    }
}

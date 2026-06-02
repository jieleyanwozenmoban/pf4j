package org.pf4j;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * ClassLoader for plugins
 */
public class PluginClassLoader extends URLClassLoader {

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public void addFile(Path file) {
        try {
            addURL(file.toUri().toURL());
        } catch (Exception e) {
            throw new PluginRuntimeException("Failed to add file to classloader: " + file, e);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}

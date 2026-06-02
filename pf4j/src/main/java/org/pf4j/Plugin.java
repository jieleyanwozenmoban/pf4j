package org.pf4j;

/**
 * Base plugin interface that all plugins must implement
 */
public interface Plugin {
    void start();
    void stop();
    void delete();
}

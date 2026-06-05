/*
 * Copyright (C) 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * A {@link PluginManager} that watches the plugins root directories for file changes
 * and automatically synchronizes the plugin lifecycle.
 *
 * <p>When a new plugin file (jar, zip, or directory) is detected, it is automatically
 * loaded and started. When a plugin file is deleted, the corresponding plugin is stopped
 * and unloaded. When a plugin file is modified (replaced), the old plugin is stopped and
 * unloaded, and the new version is loaded and started.
 *
 * <p>Usage:
 * <pre>
 * DirectoryWatchPluginManager manager = new DirectoryWatchPluginManager(pluginsPath);
 * manager.loadPlugins();
 * manager.startPlugins();
 * manager.startWatching();
 * // ... plugins are now automatically synced with the file system
 * manager.stopWatching();
 * </pre>
 *
 * <p>This class is not thread-safe.
 *
 * @author PF4J
 */
public class DirectoryWatchPluginManager extends DefaultPluginManager {

    private static final Logger log = LoggerFactory.getLogger(DirectoryWatchPluginManager.class);

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watching = false;
    private long watchEventDelay = 500;

    public DirectoryWatchPluginManager() {
        super();
    }

    public DirectoryWatchPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
    }

    public DirectoryWatchPluginManager(List<Path> pluginsRoots) {
        super(pluginsRoots);
    }

    /**
     * Start watching the plugins root directories for file changes.
     * When a change is detected, the plugin lifecycle is automatically synchronized.
     */
    public void startWatching() {
        if (watching) {
            log.warn("Already watching plugin directories");
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            for (Path pluginsRoot : getPluginsRoots()) {
                if (Files.notExists(pluginsRoot)) {
                    Files.createDirectories(pluginsRoot);
                }
                pluginsRoot.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                log.info("Watching plugin directory '{}'", pluginsRoot);
            }

            watching = true;
            watchThread = new Thread(this::processWatchEvents, "pf4j-directory-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            log.error("Failed to start directory watcher", e);
        }
    }

    /**
     * Stop watching the plugins root directories.
     */
    public void stopWatching() {
        watching = false;
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watchThread = null;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Failed to close watch service", e);
            }
            watchService = null;
        }
    }

    /**
     * Returns {@code true} if the directory watcher is currently running.
     */
    public boolean isWatching() {
        return watching;
    }

    /**
     * Set the delay in milliseconds before processing watch events.
     * This allows file operations to settle before attempting to load/unload plugins.
     * Default is 500ms.
     *
     * @param watchEventDelay the delay in milliseconds
     */
    public void setWatchEventDelay(long watchEventDelay) {
        this.watchEventDelay = watchEventDelay;
    }

    private void processWatchEvents() {
        while (watching) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }

                Map<Path, Set<WatchEvent.Kind<?>>> pathEvents = new LinkedHashMap<>();
                collectEvents(key, pathEvents);

                WatchKey additionalKey;
                while ((additionalKey = watchService.poll()) != null) {
                    collectEvents(additionalKey, pathEvents);
                }

                Thread.sleep(watchEventDelay);

                processCollectedEvents(pathEvents);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing watch events", e);
            }
        }
    }

    private void collectEvents(WatchKey key, Map<Path, Set<WatchEvent.Kind<?>>> pathEvents) {
        Path watchedDir = (Path) key.watchable();
        for (WatchEvent<?> event : key.pollEvents()) {
            Path contextPath = (Path) event.context();
            if (contextPath == null) {
                continue;
            }
            Path fullPath = watchedDir.resolve(contextPath);
            pathEvents.computeIfAbsent(fullPath, p -> new LinkedHashSet<>()).add(event.kind());
        }
        key.reset();
    }

    private void processCollectedEvents(Map<Path, Set<WatchEvent.Kind<?>>> pathEvents) {
        for (Map.Entry<Path, Set<WatchEvent.Kind<?>>> entry : pathEvents.entrySet()) {
            Path changedPath = entry.getKey();
            Set<WatchEvent.Kind<?>> events = entry.getValue();

            boolean deleted = events.contains(ENTRY_DELETE);
            boolean created = events.contains(ENTRY_CREATE);
            boolean modified = events.contains(ENTRY_MODIFY);

            if (deleted) {
                onPluginDeleted(changedPath);
            }

            if (created || modified) {
                if (modified && !deleted) {
                    onPluginModified(changedPath);
                } else if (created) {
                    onPluginCreated(changedPath);
                }
            }
        }
    }

    private void onPluginCreated(Path pluginPath) {
        if (!Files.exists(pluginPath)) {
            return;
        }

        String existingPluginId = findPluginIdByPath(pluginPath);
        if (existingPluginId != null) {
            log.debug("Plugin already loaded from path '{}', skipping", pluginPath);
            return;
        }

        try {
            log.info("New plugin detected at '{}', loading plugin", pluginPath);
            String pluginId = loadPlugin(pluginPath);
            startPlugin(pluginId);
        } catch (Exception e) {
            log.error("Failed to load new plugin from '{}'", pluginPath, e);
        }
    }

    private void onPluginDeleted(Path pluginPath) {
        String pluginId = findPluginIdByPath(pluginPath);
        if (pluginId != null) {
            log.info("Plugin removed at '{}', unloading plugin '{}'", pluginPath, pluginId);
            stopPlugin(pluginId);
            unloadPlugin(pluginId);
        }
    }

    private void onPluginModified(Path pluginPath) {
        String pluginId = findPluginIdByPath(pluginPath);
        if (pluginId != null) {
            log.info("Plugin modified at '{}', reloading plugin '{}'", pluginPath, pluginId);
            stopPlugin(pluginId);
            unloadPlugin(pluginId);
        }

        if (Files.exists(pluginPath)) {
            try {
                log.info("Loading modified plugin from '{}'", pluginPath);
                String newPluginId = loadPlugin(pluginPath);
                startPlugin(newPluginId);
            } catch (Exception e) {
                log.error("Failed to reload modified plugin from '{}'", pluginPath, e);
            }
        }
    }

    private String findPluginIdByPath(Path pluginPath) {
        for (PluginWrapper plugin : getPlugins()) {
            if (plugin.getPluginPath().equals(pluginPath)) {
                return plugin.getPluginId();
            }
        }

        String fileName = pluginPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".zip")) {
            int dotIndex = pluginPath.getFileName().toString().lastIndexOf(".");
            String directoryName = pluginPath.getFileName().toString().substring(0, dotIndex);
            Path expandedPath = pluginPath.resolveSibling(directoryName);
            for (PluginWrapper plugin : getPlugins()) {
                if (plugin.getPluginPath().equals(expandedPath)) {
                    return plugin.getPluginId();
                }
            }
        }

        return null;
    }

}

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
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link PluginManager} that watches the plugins directory for changes
 * and automatically synchronizes plugins when files are added, deleted, or modified.
 * <p>
 * When a new plugin jar/zip file is detected in the plugins directory, it is automatically
 * loaded and started. When a plugin file is deleted, the corresponding plugin is stopped
 * and unloaded. When a plugin file is modified (upgrade), the old version is stopped and
 * unloaded, and the new version is loaded and started.
 * <p>
 * This class is thread-safe for watch event processing, using synchronization to ensure
 * that plugin lifecycle operations are serialized.
 *
 * @author Decebal Suiu
 */
public class DirectoryWatchPluginManager extends DefaultPluginManager {

    private static final Logger log = LoggerFactory.getLogger(DirectoryWatchPluginManager.class);

    private static final long DEBOUNCE_MILLIS = 500;

    private WatchService watchService;
    private ExecutorService watchExecutor;
    private ScheduledExecutorService debounceExecutor;
    private final Map<Path, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DirectoryWatchPluginManager() {
        super();
    }

    public DirectoryWatchPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
    }

    /**
     * Starts watching the plugins directories for changes.
     * Existing plugins are loaded and started first, then the watch service begins monitoring.
     *
     * @throws IOException if the watch service cannot be initialized
     */
    public void startWatching() throws IOException {
        if (running.get()) {
            log.warn("Directory watching is already running");
            return;
        }

        log.info("Starting directory watch on plugins roots: {}", getPluginsRoots());

        loadPlugins();
        startPlugins();

        watchService = FileSystems.getDefault().newWatchService();
        for (Path pluginsRoot : getPluginsRoots()) {
            if (Files.isDirectory(pluginsRoot)) {
                pluginsRoot.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
                log.debug("Registered watch on '{}'", pluginsRoot);
            } else {
                log.warn("Plugins root '{}' is not a directory, cannot watch", pluginsRoot);
            }
        }

        running.set(true);
        debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pf4j-debounce");
            t.setDaemon(true);
            return t;
        });
        watchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pf4j-directory-watcher");
            t.setDaemon(true);
            return t;
        });
        watchExecutor.submit(this::watchLoop);
    }

    /**
     * Stops watching the plugins directories and shuts down the watch service.
     */
    public void stopWatching() {
        if (!running.get()) {
            return;
        }

        log.info("Stopping directory watch");
        running.set(false);

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Error closing watch service", e);
            }
        }

        for (ScheduledFuture<?> task : pendingTasks.values()) {
            task.cancel(false);
        }
        pendingTasks.clear();

        shutdownQuietly(watchExecutor);
        shutdownQuietly(debounceExecutor);
    }

    @Override
    public void stopPlugins() {
        stopWatching();
        super.stopPlugins();
    }

    private void watchLoop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                log.debug("Watch service closed, exiting watch loop");
                break;
            }

            if (key == null) {
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    log.warn("File system event overflow, some changes may have been missed");
                    continue;
                }

                Path dir = (Path) key.watchable();
                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path filename = pathEvent.context();
                Path fullPath = dir.resolve(filename);

                if (!isPluginFile(fullPath)) {
                    continue;
                }

                scheduleDebounced(fullPath);
            }

            boolean valid = key.reset();
            if (!valid) {
                log.warn("Watch key is no longer valid, directory may have been deleted");
            }
        }
    }

    private void scheduleDebounced(Path path) {
        ScheduledFuture<?> existing = pendingTasks.remove(path);
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> task = debounceExecutor.schedule(() -> {
            pendingTasks.remove(path);
            processFileEvent(path);
        }, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);

        pendingTasks.put(path, task);
    }

    private synchronized void processFileEvent(Path path) {
        try {
            boolean exists = Files.exists(path);
            String pluginId = idForPath(path);

            if (exists && pluginId != null) {
                log.info("Plugin file '{}' modified, reloading plugin '{}'", path, pluginId);
                reloadPlugin(pluginId, path);
            } else if (exists) {
                log.info("New plugin file '{}' detected, loading", path);
                loadAndStartPlugin(path);
            } else if (pluginId != null) {
                log.info("Plugin file '{}' deleted, unloading plugin '{}'", path, pluginId);
                stopAndUnloadPlugin(pluginId);
            }
        } catch (Exception e) {
            log.error("Error processing file event for '{}'", path, e);
        }
    }

    private void loadAndStartPlugin(Path path) {
        try {
            String pluginId = loadPlugin(path);
            PluginState state = startPlugin(pluginId);
            if (state.isStarted()) {
                log.info("Plugin '{}' loaded and started successfully", pluginId);
            } else {
                log.warn("Plugin '{}' loaded but failed to start (state: {})", pluginId, state);
            }
        } catch (Exception e) {
            log.error("Failed to load plugin from '{}'", path, e);
        }
    }

    private void stopAndUnloadPlugin(String pluginId) {
        try {
            PluginWrapper plugin = getPlugin(pluginId);
            if (plugin != null) {
                PluginState state = stopPlugin(pluginId);
                log.debug("Plugin '{}' stopped with state: {}", pluginId, state);
            }
            boolean unloaded = unloadPlugin(pluginId);
            if (unloaded) {
                log.info("Plugin '{}' stopped and unloaded successfully", pluginId);
            }
        } catch (Exception e) {
            log.error("Failed to stop/unload plugin '{}'", pluginId, e);
        }
    }

    private void reloadPlugin(String pluginId, Path path) {
        stopAndUnloadPlugin(pluginId);

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        loadAndStartPlugin(path);
    }

    private boolean isPluginFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private void shutdownQuietly(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
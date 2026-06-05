package org.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DirectoryWatchPluginManager extends DefaultPluginManager implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DirectoryWatchPluginManager.class);

    private static final long WATCH_BATCH_WINDOW_MILLIS = 150L;

    private final Map<WatchKey, Path> watchedDirectories = new HashMap<>();
    private final Map<String, PluginSnapshot> pluginSnapshots = new HashMap<>();

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watching;

    public DirectoryWatchPluginManager() {
        super();
        startWatcher();
    }

    public DirectoryWatchPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
        startWatcher();
    }

    public DirectoryWatchPluginManager(List<Path> pluginsRoots) {
        super(pluginsRoots);
        startWatcher();
    }

    @Override
    public synchronized void loadPlugins() {
        super.loadPlugins();
        refreshPluginSnapshots();
    }

    @Override
    public synchronized String loadPlugin(Path pluginPath) {
        String pluginId = super.loadPlugin(pluginPath);
        refreshPluginSnapshots();
        return pluginId;
    }

    @Override
    public synchronized void unloadPlugins() {
        super.unloadPlugins();
        refreshPluginSnapshots();
    }

    @Override
    public synchronized boolean unloadPlugin(String pluginId) {
        boolean unloaded = super.unloadPlugin(pluginId);
        refreshPluginSnapshots();
        return unloaded;
    }

    @Override
    public synchronized void startPlugins() {
        super.startPlugins();
        refreshPluginSnapshots();
    }

    @Override
    public synchronized PluginState startPlugin(String pluginId) {
        PluginState pluginState = super.startPlugin(pluginId);
        refreshPluginSnapshots();
        return pluginState;
    }

    @Override
    public synchronized void stopPlugins() {
        super.stopPlugins();
        refreshPluginSnapshots();
    }

    @Override
    public synchronized PluginState stopPlugin(String pluginId) {
        PluginState pluginState = super.stopPlugin(pluginId);
        refreshPluginSnapshots();
        return pluginState;
    }

    @Override
    public synchronized boolean deletePlugin(String pluginId) {
        boolean deleted = super.deletePlugin(pluginId);
        refreshPluginSnapshots();
        return deleted;
    }

    public synchronized void synchronizePlugins() {
        synchronizePlugins(Collections.emptySet());
    }

    @Override
    public synchronized void close() {
        watching = false;

        if (watchThread != null) {
            watchThread.interrupt();
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                throw new PluginRuntimeException(e, "Cannot close watch service");
            }
        }

        if (watchThread != null && watchThread.isAlive()) {
            try {
                watchThread.join(WATCH_BATCH_WINDOW_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private synchronized void startWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerPluginsRoots();
            refreshPluginSnapshots();
        } catch (IOException e) {
            throw new PluginRuntimeException(e, "Cannot start directory watch service");
        }

        watching = true;
        watchThread = new Thread(this::watchLoop, "pf4j-directory-watch");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void watchLoop() {
        while (watching) {
            WatchKey watchKey;
            try {
                watchKey = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }

            Set<Path> changedPaths = new LinkedHashSet<>();
            collectWatchEvents(watchKey, changedPaths);
            drainPendingEvents(changedPaths);

            if (changedPaths.isEmpty()) {
                continue;
            }

            try {
                synchronizePlugins(changedPaths);
            } catch (RuntimeException e) {
                log.error("Cannot synchronize plugins after directory change {}", changedPaths, e);
            }
        }
    }

    private void drainPendingEvents(Set<Path> changedPaths) {
        while (watching) {
            WatchKey watchKey;
            try {
                watchKey = watchService.poll(WATCH_BATCH_WINDOW_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }

            if (watchKey == null) {
                return;
            }

            collectWatchEvents(watchKey, changedPaths);
        }
    }

    private void collectWatchEvents(WatchKey watchKey, Set<Path> changedPaths) {
        Path watchedDirectory = watchedDirectories.get(watchKey);
        if (watchedDirectory == null) {
            watchKey.reset();
            return;
        }

        for (WatchEvent<?> event : watchKey.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                changedPaths.add(watchedDirectory);
                continue;
            }

            Path changedPath = watchedDirectory.resolve((Path) event.context()).toAbsolutePath().normalize();
            changedPaths.add(changedPath);

            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                registerIfDirectory(changedPath);
            }
        }

        if (!watchKey.reset()) {
            watchedDirectories.remove(watchKey);
        }
    }

    private void registerPluginsRoots() throws IOException {
        for (Path pluginsRoot : getPluginsRoots()) {
            registerIfDirectory(pluginsRoot.toAbsolutePath().normalize());
        }
    }

    private void registerIfDirectory(Path path) {
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return;
        }

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    registerDirectory(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new PluginRuntimeException(e, "Cannot register '{}' for watching", path);
        }
    }

    private void registerDirectory(Path directory) throws IOException {
        WatchKey watchKey = directory.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        );
        watchedDirectories.put(watchKey, directory.toAbsolutePath().normalize());
    }

    private synchronized void synchronizePlugins(Set<Path> changedPaths) {
        Map<String, DiscoveredPlugin> discoveredPlugins = discoverPlugins();
        List<PluginWrapper> loadedPlugins = new ArrayList<>(getPlugins());
        Set<String> loadedPluginIds = loadedPlugins.stream()
            .map(PluginWrapper::getPluginId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> startedPluginIds = getStartedPlugins().stream()
            .map(PluginWrapper::getPluginId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> pluginsToStart = discoveredPlugins.keySet().stream()
            .filter(pluginId -> startedPluginIds.contains(pluginId) || !loadedPluginIds.contains(pluginId))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        for (PluginWrapper plugin : loadedPlugins) {
            DiscoveredPlugin discoveredPlugin = discoveredPlugins.get(plugin.getPluginId());
            PluginSnapshot pluginSnapshot = pluginSnapshots.get(plugin.getPluginId());
            if (requiresReload(plugin, discoveredPlugin, pluginSnapshot, changedPaths)) {
                super.unloadPlugin(plugin.getPluginId());
            }
        }

        boolean loaded = false;
        for (DiscoveredPlugin discoveredPlugin : discoveredPlugins.values()) {
            if (getPlugin(discoveredPlugin.pluginId) != null) {
                continue;
            }

            try {
                loadPluginFromPath(discoveredPlugin.path);
                loaded = true;
            } catch (RuntimeException e) {
                log.warn("Cannot load plugin from '{}' during directory synchronization", discoveredPlugin.path, e);
            }
        }

        if (loaded) {
            resolvePlugins();
        }

        for (String pluginId : pluginsToStart) {
            if (getPlugin(pluginId) == null) {
                continue;
            }

            try {
                super.startPlugin(pluginId);
            } catch (RuntimeException e) {
                log.warn("Cannot start plugin '{}' during directory synchronization", pluginId, e);
            }
        }

        refreshPluginSnapshots();
    }

    private boolean requiresReload(PluginWrapper plugin, DiscoveredPlugin discoveredPlugin, PluginSnapshot pluginSnapshot, Set<Path> changedPaths) {
        if (discoveredPlugin == null) {
            return true;
        }

        Path pluginPath = plugin.getPluginPath().toAbsolutePath().normalize();
        if (!pluginPath.equals(discoveredPlugin.path)) {
            return true;
        }

        if (pluginSnapshot == null) {
            return isPathAffected(pluginPath, changedPaths);
        }

        return !pluginSnapshot.matches(discoveredPlugin.signature);
    }

    private boolean isPathAffected(Path pluginPath, Set<Path> changedPaths) {
        for (Path changedPath : changedPaths) {
            Path normalizedPath = changedPath.toAbsolutePath().normalize();
            if (normalizedPath.startsWith(pluginPath) || pluginPath.startsWith(normalizedPath)) {
                return true;
            }
        }

        return false;
    }

    private Map<String, DiscoveredPlugin> discoverPlugins() {
        Map<String, DiscoveredPlugin> discoveredPlugins = new LinkedHashMap<>();
        for (Path pluginPath : pluginRepository.getPluginPaths()) {
            try {
                PluginDescriptor descriptor = getPluginDescriptorFinder().find(pluginPath);
                String pluginId = descriptor.getPluginId();
                discoveredPlugins.put(pluginId, new DiscoveredPlugin(pluginId, pluginPath.toAbsolutePath().normalize(), createSignature(pluginPath)));
            } catch (RuntimeException e) {
                log.warn("Cannot inspect plugin at '{}' during directory synchronization", pluginPath, e);
            }
        }

        return discoveredPlugins;
    }

    private void refreshPluginSnapshots() {
        pluginSnapshots.clear();
        for (PluginWrapper plugin : getPlugins()) {
            Path pluginPath = plugin.getPluginPath();
            if (!Files.exists(pluginPath)) {
                continue;
            }

            try {
                pluginSnapshots.put(plugin.getPluginId(), new PluginSnapshot(createSignature(pluginPath)));
            } catch (RuntimeException e) {
                log.warn("Cannot refresh snapshot for plugin '{}'", plugin.getPluginId(), e);
            }
        }
    }

    private String createSignature(Path pluginPath) {
        Path normalizedPath = pluginPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedPath)) {
            return "missing";
        }

        if (Files.isRegularFile(normalizedPath)) {
            try {
                return normalizedPath + "|file|" + Files.size(normalizedPath) + "|" + Files.getLastModifiedTime(normalizedPath).toMillis();
            } catch (IOException e) {
                throw new PluginRuntimeException(e, "Cannot create signature for '{}'", normalizedPath);
            }
        }

        try (Stream<Path> paths = Files.walk(normalizedPath)) {
            return paths
                .sorted()
                .map(path -> {
                    try {
                        String type = Files.isDirectory(path) ? "dir" : "file";
                        long size = Files.isDirectory(path) ? 0L : Files.size(path);
                        long lastModified = Files.getLastModifiedTime(path).toMillis();
                        return normalizedPath.relativize(path) + "|" + type + "|" + size + "|" + lastModified;
                    } catch (IOException e) {
                        throw new PluginRuntimeException(e, "Cannot create signature for '{}'", path);
                    }
                })
                .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new PluginRuntimeException(e, "Cannot create signature for '{}'", normalizedPath);
        }
    }

    private static class DiscoveredPlugin {

        private final String pluginId;
        private final Path path;
        private final String signature;

        private DiscoveredPlugin(String pluginId, Path path, String signature) {
            this.pluginId = pluginId;
            this.path = path;
            this.signature = signature;
        }

    }

    private static class PluginSnapshot {

        private final String signature;

        private PluginSnapshot(String signature) {
            this.signature = signature;
        }

        private boolean matches(String otherSignature) {
            return Objects.equals(signature, otherSignature);
        }

    }

}

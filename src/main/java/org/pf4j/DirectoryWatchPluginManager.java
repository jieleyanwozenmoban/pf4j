package org.pf4j;

import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

public class DirectoryWatchPluginManager extends DefaultPluginManager {

    private static final Logger log = LoggerFactory.getLogger(DirectoryWatchPluginManager.class);

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean stopped = false;

    public DirectoryWatchPluginManager() {
        super();
        initWatcher();
    }

    public DirectoryWatchPluginManager(Path... pluginsRoots) {
        super(pluginsRoots);
        initWatcher();
    }

    public DirectoryWatchPluginManager(List<Path> pluginsRoots) {
        super(pluginsRoots);
        initWatcher();
    }

    private void initWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            for (Path root : pluginsRoots) {
                if (java.nio.file.Files.exists(root)) {
                    root.register(watchService, 
                            StandardWatchEventKinds.ENTRY_CREATE, 
                            StandardWatchEventKinds.ENTRY_DELETE, 
                            StandardWatchEventKinds.ENTRY_MODIFY);
                }
            }
            watchThread = new Thread(() -> {
                while (!stopped) {
                    try {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path context = ev.context();
                            Path root = (Path) key.watchable();
                            Path fullPath = root.resolve(context);
                            
                            handleEvent(kind, fullPath);
                        }
                        boolean valid = key.reset();
                        if (!valid) {
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Error watching plugins directory", e);
                    }
                }
            }, "pf4j-dir-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            log.error("Failed to initialize watch service", e);
        }
    }

    protected void handleEvent(WatchEvent.Kind<?> kind, Path path) {
        String pathStr = path.toString().toLowerCase();
        if (!pathStr.endsWith(".jar") && !pathStr.endsWith(".zip")) {
            return;
        }

        log.info("Detected {} on {}", kind, path);
        
        try {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                String pluginId = loadPlugin(path);
                if (pluginId != null) {
                    startPlugin(pluginId);
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                String pluginId = getPluginIdForPath(path);
                if (pluginId != null) {
                    unloadPlugin(pluginId);
                }
                pluginId = loadPlugin(path);
                if (pluginId != null) {
                    startPlugin(pluginId);
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                String pluginId = getPluginIdForPath(path);
                if (pluginId != null) {
                    deletePlugin(pluginId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle event {} for {}", kind, path, e);
        }
    }

    protected String getPluginIdForPath(Path path) {
        Path expectedPath = getExpectedPluginPath(path);
        for (PluginWrapper plugin : getPlugins()) {
            if (plugin.getPluginPath().equals(expectedPath) || plugin.getPluginPath().equals(path)) {
                return plugin.getPluginId();
            }
        }
        return null;
    }

    protected Path getExpectedPluginPath(Path path) {
        String pathStr = path.toString().toLowerCase();
        if (pathStr.endsWith(".zip")) {
            String fileName = path.getFileName().toString();
            String directoryName = fileName.substring(0, fileName.lastIndexOf("."));
            return path.resolveSibling(directoryName);
        }
        return path;
    }

    @Override
    public void stopPlugins() {
        super.stopPlugins();
        stopped = true;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Failed to close watch service", e);
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }
}
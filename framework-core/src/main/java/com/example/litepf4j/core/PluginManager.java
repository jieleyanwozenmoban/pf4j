package com.example.litepf4j.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PluginManager {
    private final Path pluginsDirectory;
    private final Path extractedDirectory;
    private final PluginDescriptorParser descriptorParser;
    private final ExtensionRegistry extensionRegistry;
    private final Map<String, PluginWrapper> plugins = new LinkedHashMap<>();

    public PluginManager(Path pluginsDirectory) {
        this(pluginsDirectory, new PluginDescriptorParser(), new ExtensionRegistry());
    }

    public PluginManager(Path pluginsDirectory, PluginDescriptorParser descriptorParser, ExtensionRegistry extensionRegistry) {
        this.pluginsDirectory = pluginsDirectory;
        this.extractedDirectory = pluginsDirectory.resolve(".runtime");
        this.descriptorParser = descriptorParser;
        this.extensionRegistry = extensionRegistry;
    }

    public void loadPlugins() {
        try {
            Files.createDirectories(pluginsDirectory);
            Files.createDirectories(extractedDirectory);

            try (Stream<Path> archives = Files.list(pluginsDirectory)) {
                archives.filter(path -> path.getFileName().toString().endsWith(".zip"))
                        .sorted()
                        .forEach(this::loadPluginArchive);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to scan plugin directory: " + pluginsDirectory, exception);
        }
    }

    public Set<String> getPluginIds() {
        return new LinkedHashSet<>(plugins.keySet());
    }

    public List<PluginWrapper> getPlugins() {
        return List.copyOf(plugins.values());
    }

    public PluginWrapper getPlugin(String pluginId) {
        return plugins.get(pluginId);
    }

    public <T extends ExtensionPoint> List<T> getExtensions(Class<T> extensionType) {
        return extensionRegistry.getExtensions(extensionType);
    }

    public <T extends ExtensionPoint> List<T> getExtensions(Class<T> extensionType, String pluginId) {
        return extensionRegistry.getExtensions(extensionType, pluginId);
    }

    public <T extends ExtensionPoint> Map<String, List<T>> getExtensionsByPlugin(Class<T> extensionType) {
        return extensionRegistry.getExtensionsByPlugin(extensionType);
    }

    public Map<String, Map<Class<? extends ExtensionPoint>, List<ExtensionPoint>>> getExtensionSnapshot() {
        return extensionRegistry.snapshotByPlugin();
    }

    private void loadPluginArchive(Path archivePath) {
        try {
            Path extractedPath = extractPluginArchive(archivePath);
            PluginDescriptor descriptor = descriptorParser.parse(extractedPath.resolve("plugin.properties"));
            ensureUniquePluginId(descriptor.pluginId(), archivePath);

            Path classesPath = extractedPath.resolve("classes");
            if (!Files.isDirectory(classesPath)) {
                throw new IllegalArgumentException("Plugin classes directory not found: " + classesPath);
            }

            PluginClassLoader classLoader = new PluginClassLoader(
                    descriptor.pluginId(),
                    classesPath,
                    Thread.currentThread().getContextClassLoader()
            );

            PluginWrapper pluginWrapper = new PluginWrapper(descriptor, archivePath, extractedPath, classesPath, classLoader);
            registerExtensions(pluginWrapper);
            plugins.put(descriptor.pluginId(), pluginWrapper);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load plugin archive: " + archivePath, exception);
        }
    }

    private void ensureUniquePluginId(String pluginId, Path archivePath) {
        if (plugins.containsKey(pluginId)) {
            throw new IllegalStateException("Duplicate plugin id '" + pluginId + "' from archive " + archivePath);
        }
    }

    private Path extractPluginArchive(Path archivePath) throws IOException {
        String directoryName = archivePath.getFileName().toString().replaceFirst("\\.zip$", "");
        Path targetDirectory = extractedDirectory.resolve(directoryName);
        deleteDirectory(targetDirectory);
        Files.createDirectories(targetDirectory);

        try (InputStream fileInputStream = Files.newInputStream(archivePath);
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                Path outputPath = targetDirectory.resolve(zipEntry.getName()).normalize();
                if (!outputPath.startsWith(targetDirectory)) {
                    throw new IllegalArgumentException("Illegal zip entry: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zipInputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        return targetDirectory;
    }

    private void registerExtensions(PluginWrapper pluginWrapper) throws IOException {
        try (Stream<Path> pathStream = Files.walk(pluginWrapper.classesPath())) {
            List<Path> classFiles = pathStream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .filter(path -> !path.getFileName().toString().contains("$"))
                    .sorted()
                    .toList();

            for (Path classFile : classFiles) {
                String className = toClassName(pluginWrapper.classesPath(), classFile);
                Class<?> candidateClass = pluginWrapper.classLoader().loadClass(className);
                if (!isConcreteExtension(candidateClass)) {
                    continue;
                }

                Extension extension = candidateClass.getAnnotation(Extension.class);
                if (extension == null) {
                    continue;
                }

                ExtensionPoint instance = (ExtensionPoint) candidateClass.getDeclaredConstructor().newInstance();
                for (Class<? extends ExtensionPoint> extensionType : collectExtensionTypes(candidateClass)) {
                    registerExtension(pluginWrapper.descriptor().pluginId(), extensionType, instance, extension.ordinal());
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate extension for plugin " + pluginWrapper.descriptor().pluginId(), exception);
        }
    }

    private boolean isConcreteExtension(Class<?> candidateClass) {
        return ExtensionPoint.class.isAssignableFrom(candidateClass)
                && !candidateClass.isInterface()
                && !Modifier.isAbstract(candidateClass.getModifiers());
    }

    private Set<Class<? extends ExtensionPoint>> collectExtensionTypes(Class<?> candidateClass) {
        Set<Class<? extends ExtensionPoint>> extensionTypes = new LinkedHashSet<>();
        collectExtensionTypes(candidateClass, extensionTypes);
        return extensionTypes;
    }

    private void collectExtensionTypes(Class<?> candidateClass, Set<Class<? extends ExtensionPoint>> extensionTypes) {
        for (Class<?> interfaceType : candidateClass.getInterfaces()) {
            if (ExtensionPoint.class.isAssignableFrom(interfaceType) && interfaceType != ExtensionPoint.class) {
                @SuppressWarnings("unchecked")
                Class<? extends ExtensionPoint> castedType = (Class<? extends ExtensionPoint>) interfaceType;
                extensionTypes.add(castedType);
            }
            collectExtensionTypes(interfaceType, extensionTypes);
        }

        Class<?> superClass = candidateClass.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            if (ExtensionPoint.class.isAssignableFrom(superClass) && superClass != ExtensionPoint.class) {
                @SuppressWarnings("unchecked")
                Class<? extends ExtensionPoint> castedType = (Class<? extends ExtensionPoint>) superClass;
                extensionTypes.add(castedType);
            }
            collectExtensionTypes(superClass, extensionTypes);
        }
    }

    private <T extends ExtensionPoint> void registerExtension(String pluginId, Class<T> extensionType, ExtensionPoint instance, int ordinal) {
        extensionRegistry.register(pluginId, extensionType, extensionType.cast(instance), ordinal);
    }

    private String toClassName(Path classesRoot, Path classFile) {
        Path relativePath = classesRoot.relativize(classFile);
        String className = relativePath.toString()
                .replace(classFile.getFileSystem().getSeparator(), ".")
                .replaceFirst("\\.class$", "");
        return className;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(directory)) {
            for (Path path : pathStream.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.deleteIfExists(path);
            }
        }
    }
}

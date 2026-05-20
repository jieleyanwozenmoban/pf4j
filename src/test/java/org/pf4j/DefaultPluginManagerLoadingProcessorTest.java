package org.pf4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.test.PluginZip;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultPluginManagerLoadingProcessorTest {

    @TempDir
    Path pluginsPath;

    @Test
    void loadPluginUsesComposableProcessorChain() throws IOException {
        PluginZip pluginZip = new PluginZip.Builder(pluginsPath.resolve("my-plugin-1.0.0.zip"), "myPlugin")
            .pluginVersion("1.0.0")
            .build();

        List<String> invocations = new ArrayList<>();
        DefaultPluginManager pluginManager = new DefaultPluginManager(pluginsPath) {
            @Override
            protected List<PluginLoadingProcessor> createPluginLoadingProcessors() {
                List<PluginLoadingProcessor> processors = new ArrayList<>();
                processors.add(context -> {
                    invocations.add("before-core");
                    return true;
                });
                processors.addAll(super.createPluginLoadingProcessors());
                processors.add(context -> {
                    invocations.add("after-core");
                    return true;
                });
                return processors;
            }

            @Override
            protected List<PluginLoadingProcessor> createLoadPluginProcessors() {
                List<PluginLoadingProcessor> processors = new ArrayList<>();
                processors.add(context -> {
                    invocations.add("validate-path");
                    return new ValidatePluginPathProcessor().process(context);
                });
                processors.addAll(createPluginLoadingProcessors());
                processors.add(context -> {
                    invocations.add("resolve-dependencies");
                    return new ResolvePluginDependenciesProcessor().process(context);
                });
                return processors;
            }
        };

        pluginManager.loadPlugin(pluginZip.path());

        assertEquals(
            Arrays.asList("validate-path", "before-core", "after-core", "resolve-dependencies"),
            invocations
        );
    }

    @Test
    void loadPluginPreservesResolvedEventParameters() throws IOException {
        PluginZip pluginZip = new PluginZip.Builder(pluginsPath.resolve("my-plugin-1.0.0.zip"), "myPlugin")
            .pluginVersion("1.0.0")
            .build();

        List<PluginStateEvent> events = new ArrayList<>();
        DefaultPluginManager pluginManager = new DefaultPluginManager(pluginsPath);
        pluginManager.addPluginStateListener(events::add);

        pluginManager.loadPlugin(pluginZip.path());

        assertEquals(1, events.size());
        PluginStateEvent event = events.get(0);
        assertEquals("myPlugin", event.getPlugin().getPluginId());
        assertEquals(PluginState.CREATED, event.getOldState());
        assertEquals(PluginState.RESOLVED, event.getPluginState());
    }

}

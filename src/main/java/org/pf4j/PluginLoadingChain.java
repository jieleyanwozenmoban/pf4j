package org.pf4j;

import java.util.ArrayList;
import java.util.List;

public class PluginLoadingChain {
    private final List<PluginLoadingProcessor> processors = new ArrayList<>();

    public PluginLoadingChain addProcessor(PluginLoadingProcessor processor) {
        processors.add(processor);
        return this;
    }

    public void process(PluginLoadingContext context, AbstractPluginManager pluginManager) {
        for (PluginLoadingProcessor processor : processors) {
            processor.process(context, pluginManager);
        }
    }
}

package org.pf4j;

import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveExtractionProcessor implements PluginLoadingProcessor {
    private static final Logger log = LoggerFactory.getLogger(ArchiveExtractionProcessor.class);

    @Override
    public void process(PluginLoadingContext context, AbstractPluginManager pluginManager) {
        try {
            context.setPluginPath(FileUtils.expandIfZip(context.getPluginPath()));
        } catch (Exception e) {
            log.warn("Failed to unzip {}", context.getPluginPath(), e);
            throw new PluginRuntimeException(e, "Failed to unzip plugin");
        }
    }
}

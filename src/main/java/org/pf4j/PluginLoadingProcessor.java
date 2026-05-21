package org.pf4j;

/**
 * Processor interface for the plugin loading chain.
 */
public interface PluginLoadingProcessor {

    /**
     * Process a phase of the plugin loading process.
     * 
     * @param context the plugin loading context
     */
    void process(PluginLoadingContext context);

}

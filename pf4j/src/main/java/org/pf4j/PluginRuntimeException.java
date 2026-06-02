package org.pf4j;

/**
 * Runtime exception for plugin operations
 */
public class PluginRuntimeException extends PluginException {

    public PluginRuntimeException(String message) {
        super(message);
    }

    public PluginRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public PluginRuntimeException(String format, Object... args) {
        super(String.format(format, args));
    }
}

package org.pf4j;

/**
 * Plugin state enumeration
 */
public enum PluginState {
    CREATED("CREATED"),
    DISABLED("DISABLED"),
    STARTED("STARTED"),
    STOPPED("STOPPED"),
    DELETED("DELETED");

    private final String status;

    PluginState(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public static PluginState fromStatus(String status) {
        for (PluginState state : values()) {
            if (state.getStatus().equals(status)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Invalid plugin state: " + status);
    }
}

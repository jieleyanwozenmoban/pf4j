package org.pf4j;

import java.nio.file.Path;

/**
 * Runtime mode (DEVELOPMENT or DEPLOYMENT)
 */
public enum RuntimeMode {
    DEVELOPMENT("development"),
    DEPLOYMENT("deployment");

    private final String mode;

    RuntimeMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }
}

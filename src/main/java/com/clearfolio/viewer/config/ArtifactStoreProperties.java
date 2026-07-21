package com.clearfolio.viewer.config;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration values that control where conversion artifacts are stored.
 */
@ConfigurationProperties(prefix = "clearfolio.artifact-store")
public class ArtifactStoreProperties {

    /**
     * Mode value selecting the disk-backed artifact store.
     */
    public static final String MODE_FILESYSTEM = "filesystem";

    /**
     * Mode value selecting the in-memory artifact store.
     */
    public static final String MODE_IN_MEMORY = "in-memory";

    private static final String DEFAULT_ROOT_DIR = "data/artifacts";

    private String mode = MODE_FILESYSTEM;
    private String rootDir = DEFAULT_ROOT_DIR;

    /**
     * Returns the configured artifact store mode.
     *
     * @return artifact store mode
     */
    public String getMode() {
        return mode;
    }

    /**
     * Sets the artifact store mode; blank or null values fall back to the
     * disk-backed default, and any value other than {@link #MODE_IN_MEMORY}
     * selects the disk-backed store.
     *
     * @param mode artifact store mode
     */
    public void setMode(String mode) {
        String sanitized = sanitize(mode);
        this.mode = sanitized.isEmpty() ? MODE_FILESYSTEM : sanitized;
    }

    /**
     * Returns the root directory used by the disk-backed artifact store.
     *
     * @return artifact storage root directory
     */
    public String getRootDir() {
        return rootDir;
    }

    /**
     * Sets the root directory used by the disk-backed artifact store; blank or
     * null values fall back to the default working data directory.
     *
     * @param rootDir artifact storage root directory
     */
    public void setRootDir(String rootDir) {
        if (rootDir == null) {
            this.rootDir = DEFAULT_ROOT_DIR;
            return;
        }
        String sanitized = rootDir;
        if (sanitized.indexOf('\u0000') != -1) {
            sanitized = sanitized.replace("\u0000", "");
        }
        sanitized = sanitized.strip();
        this.rootDir = sanitized.isEmpty() ? DEFAULT_ROOT_DIR : sanitized;
    }

    /**
     * Returns whether the in-memory artifact store mode is selected.
     *
     * @return true when mode selects the in-memory store
     */
    public boolean isInMemoryMode() {
        return MODE_IN_MEMORY.equals(mode);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value;
        if (sanitized.indexOf('\u0000') != -1) {
            sanitized = sanitized.replace("\u0000", "");
        }
        return sanitized.strip().toLowerCase(Locale.ROOT);
    }
}

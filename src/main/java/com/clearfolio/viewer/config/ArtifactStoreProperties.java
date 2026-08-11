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
     * Creates artifact-store properties with restart-surviving filesystem defaults.
     */
    public ArtifactStoreProperties() {
        // Field initializers provide the secure deployment defaults.
    }

    /**
     * Returns the configured artifact store mode.
     *
     * @return artifact store mode
     */
    public String getMode() {
        return mode;
    }

    /**
     * Sets the artifact store mode. Blank or null values fall back to the
     * disk-backed default; unsupported non-blank modes are rejected so a
     * deployment cannot silently persist artifacts to a different store than
     * the operator intended.
     *
     * @param mode artifact store mode
     * @throws IllegalArgumentException when a non-blank mode is unsupported
     */
    public void setMode(String mode) {
        String sanitized = sanitize(mode);
        if (sanitized.isEmpty()) {
            this.mode = MODE_FILESYSTEM;
            return;
        }
        if (!MODE_FILESYSTEM.equals(sanitized) && !MODE_IN_MEMORY.equals(sanitized)) {
            throw new IllegalArgumentException("unsupported artifact store mode: " + sanitized);
        }
        this.mode = sanitized;
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
        String sanitized = rootDir == null ? "" : rootDir.replace("\u0000", "").strip();
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
        return value.replace("\u0000", "").strip().toLowerCase(Locale.ROOT);
    }
}

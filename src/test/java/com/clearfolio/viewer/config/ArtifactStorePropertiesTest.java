package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArtifactStorePropertiesTest {

    @Test
    void defaultsSelectDiskBackedStoreUnderWorkingDataDir() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();

        assertEquals(ArtifactStoreProperties.MODE_FILESYSTEM, properties.getMode());
        assertEquals("data/artifacts", properties.getRootDir());
        assertFalse(properties.isInMemoryMode());
    }

    @Test
    void setModeNormalizesCaseAndWhitespace() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();

        properties.setMode("  IN-MEMORY  ");

        assertEquals(ArtifactStoreProperties.MODE_IN_MEMORY, properties.getMode());
        assertTrue(properties.isInMemoryMode());
    }

    @Test
    void setModeFallsBackToFilesystemForNullAndBlankValues() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();

        properties.setMode(null);
        assertEquals(ArtifactStoreProperties.MODE_FILESYSTEM, properties.getMode());

        properties.setMode("   ");
        assertEquals(ArtifactStoreProperties.MODE_FILESYSTEM, properties.getMode());
    }

    @Test
    void setModeKeepsUnknownValuesWithoutSelectingInMemory() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();

        properties.setMode("cloud");

        assertEquals("cloud", properties.getMode());
        assertFalse(properties.isInMemoryMode());
    }

    @Test
    void setRootDirFallsBackToDefaultForNullAndBlankValues() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();

        properties.setRootDir(null);
        assertEquals("data/artifacts", properties.getRootDir());

        properties.setRootDir("   ");
        assertEquals("data/artifacts", properties.getRootDir());
    }

    @Test
    void setRootDirStripsControlCharactersAndKeepsValue() {
        ArtifactStoreProperties properties = new ArtifactStoreProperties();

        properties.setRootDir("  /var/clearfolio/artifacts\u0000  ");

        assertEquals("/var/clearfolio/artifacts", properties.getRootDir());
    }
}

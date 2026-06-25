package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ConversionPropertiesTest {

    @Test
    void allowedExtensionsHasDefaultValue() {
        ConversionProperties properties = new ConversionProperties();
        assertEquals(Set.of("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "rtf", "csv"), properties.getAllowedExtensions());
    }

    @Test
    void setAllowedExtensionsNormalizesValues() {
        ConversionProperties properties = new ConversionProperties();

        Set<String> input = new LinkedHashSet<>();
        input.add("PDF");
        input.add(null);
        input.add("  doc  ");
        input.add("\u0000");

        properties.setAllowedExtensions(input);

        Set<String> expected = new LinkedHashSet<>(Set.of("pdf", "doc"));
        assertEquals(expected, properties.getAllowedExtensions());
    }

    @Test
    void policyOverrideSecretHasDefaultValue() {
        ConversionProperties properties = new ConversionProperties();
        assertEquals("default-secret-change-in-prod", properties.getPolicyOverrideSecret());
    }

    @Test
    void setPolicyOverrideSecretUpdatesValue() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret("new-secret");
        assertEquals("new-secret", properties.getPolicyOverrideSecret());
    }

    @Test
    void setPolicyOverrideSecretThrowsOnBlank() {
        ConversionProperties properties = new ConversionProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setPolicyOverrideSecret(""));
        assertThrows(IllegalArgumentException.class, () -> properties.setPolicyOverrideSecret(null));
        assertThrows(IllegalArgumentException.class, () -> properties.setPolicyOverrideSecret("   "));
    }

    @Test
    void workerThreadsClampsToAtLeastOne() {
        ConversionProperties properties = new ConversionProperties();

        properties.setWorkerThreads(0);

        assertEquals(1, properties.getWorkerThreads());
    }

    @Test
    void queueCapacityClampsToAtLeastOne() {
        ConversionProperties properties = new ConversionProperties();

        properties.setQueueCapacity(-10);

        assertEquals(1, properties.getQueueCapacity());
    }

    @Test
    void blockedExtensionsAreNormalizedAndSanitized() {
        ConversionProperties properties = new ConversionProperties();

        properties.setBlockedExtensions(Set.of(" HWP ", "\u0000HwPx", " "));

        assertEquals(2, properties.getBlockedExtensions().size());
        assertTrue(properties.getBlockedExtensions().contains("hwp"));
        assertTrue(properties.getBlockedExtensions().contains("hwpx"));
    }

    @Test
    void blockedExtensionsIgnoresNullAndBlankValues() {
        ConversionProperties properties = new ConversionProperties();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(null);
        values.add(" ");
        values.add("\u0000");
        values.add(" DOCX ");

        properties.setBlockedExtensions(values);

        assertEquals(1, properties.getBlockedExtensions().size());
        assertTrue(properties.getBlockedExtensions().contains("docx"));
    }

    @Test
    void blockedExtensionsBecomesEmptyWhenInputIsNull() {
        ConversionProperties properties = new ConversionProperties();

        properties.setBlockedExtensions(null);

        assertTrue(properties.getBlockedExtensions().isEmpty());
    }
}

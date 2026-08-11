package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ConversionPropertiesTest {

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
    void processingLeaseTimeoutClampsToAtLeastOneMillisecond() {
        ConversionProperties properties = new ConversionProperties();

        properties.setProcessingLeaseTimeoutMs(0L);

        assertEquals(1L, properties.getProcessingLeaseTimeoutMs());
    }

    @Test
    void retryBackoffMultiplierRejectsNonFiniteValues() {
        ConversionProperties properties = new ConversionProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setRetryBackoffMultiplier(Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setRetryBackoffMultiplier(Double.POSITIVE_INFINITY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setRetryBackoffMultiplier(Double.NEGATIVE_INFINITY)
        );
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

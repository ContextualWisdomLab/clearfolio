package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the supported runtime contains exactly one Commons Logging API provider.
 */
final class CommonsLoggingRuntimeBindingTest {

    @Test
    void commonsLoggingApiIsProvidedOnlyBySpringJcl() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        List<String> providers = Collections.list(
                        loader.getResources("org/apache/commons/logging/LogFactory.class"))
                .stream()
                .map(URL::toExternalForm)
                .sorted()
                .toList();

        assertEquals(
                1,
                providers.size(),
                () -> "Expected one Commons Logging API provider but found: " + providers);
        assertTrue(
                providers.getFirst().contains("spring-jcl"),
                () -> "Expected Spring's spring-jcl bridge to own Commons Logging, but found: "
                        + providers.getFirst());
    }
}

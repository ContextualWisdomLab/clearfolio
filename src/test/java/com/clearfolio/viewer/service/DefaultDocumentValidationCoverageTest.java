package com.clearfolio.viewer.service;

import static com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.SECURITY_PROVIDERS_LOCK;
import static com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.sha256ProviderPositions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.ProviderPosition;

/**
 * Exercises security and filename-validation edge cases that production callers
 * can reach through supported configuration or hostile upload metadata.
 */
@ResourceLock("java.security.Security.providers")
class DefaultDocumentValidationCoverageTest {

    @Test
    void normalizesNullPolicySecretToTheSupportedDisabledDefault() {
        ConversionProperties properties = new ConversionProperties();

        properties.setPolicyOverrideSecret(null);

        assertEquals("", properties.getPolicyOverrideSecret());
    }

    @Test
    void rejectsBlankRootNullByteAndAlternateDataStreamFilenameShapes() throws Exception {
        DefaultDocumentValidationService service = serviceWithDefaults();

        assertEquals("", invokeString(service, "extensionOf", " "));
        assertEquals("", invokeString(service, "extensionOf", "/"));

        String nullByteFilename = "report" + (char) 0 + ".pdf";
        InvocationTargetException nullByteException = assertThrows(
                InvocationTargetException.class,
                () -> invokeString(service, "extensionOf", nullByteFilename)
        );
        assertTrue(nullByteException.getCause() instanceof IllegalArgumentException);
        assertEquals("File name contains null byte.", nullByteException.getCause().getMessage());

        InvocationTargetException alternateStreamException = assertThrows(
                InvocationTargetException.class,
                () -> invokeString(service, "extensionOf", "report.pdf:stream")
        );
        assertTrue(alternateStreamException.getCause() instanceof IllegalArgumentException);
        assertEquals("File extension is invalid.", alternateStreamException.getCause().getMessage());
    }

    @Test
    void sanitizesEveryLogSeparatorWithoutChangingTheAdjacentBoundaryCharacter() throws Exception {
        DefaultDocumentValidationService service = serviceWithDefaults();
        String hostile = "\u0000\t\r\n\u2028\u2029\u202A\u202B\u202C\u202D\u202E\u202Fx";

        String sanitized = invokeString(service, "sanitizeForLog", hostile);

        assertEquals("_".repeat(11) + "\u202Fx", sanitized);
    }

    @Test
    void auditFingerprintFailsClosedWhenSha256IsUnavailable() throws Exception {
        DefaultDocumentValidationService service = serviceWithDefaults();
        Method method = DefaultDocumentValidationService.class.getDeclaredMethod(
                "tokenFingerprint",
                String.class
        );
        method.setAccessible(true);

        synchronized (SECURITY_PROVIDERS_LOCK) {
            List<ProviderPosition> providers = sha256ProviderPositions();
            assertFalse(providers.isEmpty());
            providers.forEach(position -> Security.removeProvider(position.provider().getName()));
            try {
                InvocationTargetException exception = assertThrows(
                        InvocationTargetException.class,
                        () -> method.invoke(service, "approval-token")
                );
                assertTrue(exception.getCause() instanceof IllegalStateException);
                assertEquals("SHA-256 digest unavailable", exception.getCause().getMessage());
            } finally {
                providers.stream()
                        .sorted(Comparator.comparingInt(ProviderPosition::position))
                        .forEach(position -> Security.insertProviderAt(
                                position.provider(),
                                position.position()
                        ));
            }
        }
    }

    private static DefaultDocumentValidationService serviceWithDefaults() {
        return new DefaultDocumentValidationService(new ConversionProperties());
    }

    private static String invokeString(
            DefaultDocumentValidationService service,
            String methodName,
            String value
    ) throws Exception {
        Method method = DefaultDocumentValidationService.class.getDeclaredMethod(
                methodName,
                String.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, value);
    }
}

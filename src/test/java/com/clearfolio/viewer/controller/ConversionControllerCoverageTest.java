package com.clearfolio.viewer.controller;

import static com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.SECURITY_PROVIDERS_LOCK;
import static com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.sha256ProviderPositions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.util.unit.DataSize;

import com.clearfolio.viewer.artifact.ArtifactLinkService;
import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.ProviderPosition;

/**
 * Exercises download-filename and checksum failure paths using realistic hostile
 * metadata and a deliberately unavailable digest provider.
 */
@ResourceLock("java.security.Security.providers")
class ConversionControllerCoverageTest {

    @Test
    void downloadFilenameHandlesBlankExtensionlessAndMeaninglessNames() {
        assertEquals("document.pdf", ConversionController.pdfDownloadFilename("   "));
        assertEquals("report.pdf", ConversionController.pdfDownloadFilename("report"));
        assertEquals("document.pdf", ConversionController.pdfDownloadFilename("___"));
        assertEquals("document.pdf", ConversionController.pdfDownloadFilename("...."));
    }

    @Test
    void downloadFilenamePreservesEveryAllowedCharacterAfterAnUnsafeCharacter() {
        assertEquals(
                "safe.name.pdf",
                ConversionController.pdfDownloadFilename("safe.name.txt")
        );
        assertEquals(
                "safe_name.pdf",
                ConversionController.pdfDownloadFilename("safe_name.txt")
        );
        assertEquals(
                "bad_safe.name-test_value.pdf",
                ConversionController.pdfDownloadFilename("bad safe.name-test_value.docx")
        );
    }

    @Test
    void checksumGenerationFailsClosedWhenSha256IsUnavailable() throws Exception {
        ConversionController controller = new ConversionController(
                mock(DocumentConversionService.class),
                mock(TenantAccessService.class),
                mock(ArtifactLinkService.class),
                mock(ArtifactStore.class),
                DataSize.ofBytes(1_024L)
        );
        Method method = ConversionController.class.getDeclaredMethod("calculateSha256", byte[].class);
        method.setAccessible(true);

        synchronized (SECURITY_PROVIDERS_LOCK) {
            List<ProviderPosition> providers = sha256ProviderPositions();
            assertFalse(providers.isEmpty());
            providers.forEach(position -> Security.removeProvider(position.provider().getName()));
            try {
                InvocationTargetException exception = assertThrows(
                        InvocationTargetException.class,
                        () -> method.invoke(controller, new Object[] {new byte[] {1, 2, 3}})
                );
                assertTrue(exception.getCause() instanceof IllegalStateException);
                assertEquals(
                        "SHA-256 algorithm not available",
                        exception.getCause().getMessage()
                );
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
}

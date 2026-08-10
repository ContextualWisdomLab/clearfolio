package com.clearfolio.viewer.conversion;

import static com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.SECURITY_PROVIDERS_LOCK;
import static com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.sha256ProviderPositions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.Security;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.clearfolio.viewer.testsupport.SecurityProviderTestSupport.ProviderPosition;

/** Covers fail-closed digest behavior when the JVM cannot provide SHA-256. */
@ResourceLock("java.security.Security.providers")
class OfficeConversionDigestFailureCoverageTest {

    @Test
    void requestAndResultDigestMethodsFailClosedWithoutSha256Provider() {
        OfficeConversionRequest request = new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("2af086d7-5739-4b74-9791-5ed4a899f5e8"),
                3L,
                "docx",
                "adapter",
                "2",
                "policy",
                "trace",
                OfficeConversionTestSource.zipPackage("digest-source")
        );
        String digest = request.sourceSha256();
        OfficeConversionResult result = new OfficeConversionResult(
                "adapter",
                "2",
                digest,
                request.binding(),
                OfficeConversionTestPdf.onePage()
        );

        synchronized (SECURITY_PROVIDERS_LOCK) {
            List<ProviderPosition> providers = sha256ProviderPositions();
            assertFalse(providers.isEmpty());
            providers.forEach(position -> Security.removeProvider(position.provider().getName()));
            try {
                IllegalStateException sourceFailure = assertThrows(
                        IllegalStateException.class,
                        request::sourceSha256
                );
                assertEquals("SHA-256 is unavailable", sourceFailure.getMessage());

                IllegalStateException outputFailure = assertThrows(
                        IllegalStateException.class,
                        result::outputSha256
                );
                assertEquals("SHA-256 is unavailable", outputFailure.getMessage());
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

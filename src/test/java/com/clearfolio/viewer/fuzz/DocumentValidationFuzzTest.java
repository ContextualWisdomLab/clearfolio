package com.clearfolio.viewer.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.controller.InMemoryMultipartFile;
import com.clearfolio.viewer.service.DefaultDocumentValidationService;
import com.clearfolio.viewer.service.PolicyOverrideRequest;

/**
 * Fuzzes {@link DefaultDocumentValidationService#validateOrThrow}, the upload
 * gate that runs before any conversion work.
 *
 * <p>Every input is untrusted: the original filename (parsed for its
 * extension), the raw file bytes, and the three policy-override HTTP headers.
 * The documented contract is that invalid uploads are rejected with an
 * {@link IllegalArgumentException} (its subclass
 * {@code UnsupportedDocumentFormatException} included) -- so the invariant is
 * that no <em>other</em> throwable ever escapes for arbitrary input.
 */
final class DocumentValidationFuzzTest {

    private final DefaultDocumentValidationService validator = createValidator();

    private static DefaultDocumentValidationService createValidator() {
        ConversionProperties properties = new ConversionProperties();
        properties.setPolicyOverrideSecret("fuzz-test-secret");
        return new DefaultDocumentValidationService(properties);
    }

    @FuzzTest(maxDuration = "60s")
    void validateOrThrowOnlyRejectsWithIllegalArgument(FuzzedDataProvider data) {
        String filename = data.consumeBoolean() ? null : data.consumeString(64);
        String contentType = data.consumeBoolean() ? null : data.consumeString(32);
        byte[] content = data.consumeBytes(data.consumeInt(0, 4096));

        String policyOverride = data.consumeBoolean() ? null : data.consumeString(16);
        String approvalToken = data.consumeBoolean() ? null : data.consumeString(32);
        String approverId = data.consumeRemainingAsString();

        InMemoryMultipartFile file =
                new InMemoryMultipartFile("file", filename, contentType, content);
        PolicyOverrideRequest override =
                PolicyOverrideRequest.of(policyOverride, approvalToken, approverId);

        try {
            validator.validateOrThrow(file, override);
        } catch (IllegalArgumentException expected) {
            // Documented rejection path (covers UnsupportedDocumentFormatException).
        }
    }
}

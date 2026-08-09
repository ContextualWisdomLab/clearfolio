package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Exercises download-filename normalization paths using realistic hostile and
 * unusual source metadata.
 *
 * <p>Artifact checksum generation and fail-closed digest-provider behavior are
 * owned by {@code ArtifactLinkService}; the download controller now reuses the
 * checksum from the verified signed-token claims rather than hashing the same
 * bytes a second time.</p>
 */
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
}

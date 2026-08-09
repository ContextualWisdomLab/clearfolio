package com.clearfolio.viewer.conversion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Deterministic parseable PDF fixtures shared by Office conversion contract tests.
 */
final class OfficeConversionTestPdf {

    private OfficeConversionTestPdf() {
    }

    /**
     * Creates a deterministic one-page PDF suitable for parser acceptance tests.
     *
     * @return parseable one-page PDF bytes
     */
    static byte[] onePage() {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create test PDF", ex);
        }
    }
}

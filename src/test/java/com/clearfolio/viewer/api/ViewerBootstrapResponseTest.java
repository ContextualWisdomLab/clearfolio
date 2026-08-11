package com.clearfolio.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

class ViewerBootstrapResponseTest {

    @Test
    void reportsOnlyQualifiedPdfRendererAdapter() {
        Map<String, String> expectedMappings = Map.ofEntries(
                Map.entry("report.pdf", "PDF_JS"),
                Map.entry("report.doc", "PDF_JS"),
                Map.entry("report.docx", "PDF_JS"),
                Map.entry("report.xls", "PDF_JS"),
                Map.entry("report.xlsx", "PDF_JS"),
                Map.entry("report.csv", "PDF_JS"),
                Map.entry("report.tsv", "PDF_JS"),
                Map.entry("report.ppt", "PDF_JS"),
                Map.entry("report.pptx", "PDF_JS"),
                Map.entry("report.md", "PDF_JS"),
                Map.entry("report.txt", "PDF_JS"),
                Map.entry("report.bin", "PDF_JS")
        );

        for (Map.Entry<String, String> entry : expectedMappings.entrySet()) {
            ConversionJob job = succeededJob(entry.getKey());

            ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

            assertEquals(entry.getValue(), response.rendererAdapter());
            assertEquals("PDF_JS", response.viewerMode());
        }
    }

    @Test
    void defaultsSourceExtensionAndAdapterWhenFilenameHasNoExtension() {
        ConversionJob job = succeededJob("report");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void defaultsSourceExtensionAndAdapterWhenFilenameEndsWithDot() {
        ConversionJob job = succeededJob("report.");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void defaultsSourceExtensionAndAdapterWhenFilenameIsNull() {
        ConversionJob job = succeededJob(null);

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void defaultsSourceExtensionAndAdapterWhenFilenameIsBlank() {
        ConversionJob job = succeededJob("   ");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void defaultsSourceExtensionAndAdapterForLeadingDotFileName() {
        ConversionJob job = succeededJob(".gitignore");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void trimsFilenameBeforeExtractingExtension() {
        ConversionJob job = succeededJob("  report.docx  ");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("docx", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void normalizesSourceExtensionToLowerCase() {
        ConversionJob job = succeededJob("REPORT.DOCX");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("docx", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    private ConversionJob succeededJob(String fileName) {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                fileName,
                "application/octet-stream",
                "content-hash",
                12L
        );
        job.markSucceeded("/artifacts/result.pdf", "done");
        return job;
    }
}

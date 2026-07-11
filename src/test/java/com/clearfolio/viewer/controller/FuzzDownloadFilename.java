package com.clearfolio.viewer.controller;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

/**
 * Jazzer target for the download filename normalization path used in
 * Content-Disposition responses.
 */
public final class FuzzDownloadFilename {

    private FuzzDownloadFilename() {
    }

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String candidate = data.consumeString(1024);
        String filename = ConversionController.pdfDownloadFilename(candidate);
        if (!filename.endsWith(".pdf")) {
            throw new IllegalStateException("download filename must keep pdf extension");
        }
        if (filename.contains("\r") || filename.contains("\n") || filename.contains("\"")) {
            throw new IllegalStateException("download filename must be header-safe");
        }
    }
}

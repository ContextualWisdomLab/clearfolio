package com.clearfolio.viewer.api;

import java.time.Instant;
import java.util.Locale;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * API payload that initializes the viewer for a converted document.
 */
public record ViewerBootstrapResponse(
        String docId,
        String status,
        String fileName,
        String viewerMode,
        String previewResourcePath,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String sourceExtension,
        String rendererAdapter,
        String artifactLinkUrl,
        Instant artifactLinkExpiresAt,
        String artifactLinkScope
) {

    private static final String PDF_JS = "PDF_JS";

    /**
     * Creates a viewer bootstrap response from a conversion job.
     *
     * @param job completed conversion job
     * @return mapped viewer bootstrap payload
     */
    public static ViewerBootstrapResponse from(ConversionJob job) {
        return from(job, null);
    }

    /**
     * Creates a viewer bootstrap response from a conversion job and signed artifact link.
     *
     * @param job completed conversion job
     * @param artifactLink signed artifact link
     * @return mapped viewer bootstrap payload
     */
    public static ViewerBootstrapResponse from(ConversionJob job, ArtifactLinkResponse artifactLink) {
        String sourceExtension = sourceExtensionOf(job.getOriginalFileName());
        String rendererAdapter = rendererAdapterFor(sourceExtension);
        String previewResourcePath = artifactLink == null
                ? job.getConvertedResourcePath()
                : artifactLink.artifactUrl();
        return new ViewerBootstrapResponse(
                job.getJobId().toString(),
                job.getStatus().name(),
                job.getOriginalFileName(),
                PDF_JS,
                previewResourcePath,
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                sourceExtension,
                rendererAdapter,
                artifactLink == null ? null : artifactLink.artifactUrl(),
                artifactLink == null ? null : artifactLink.expiresAt(),
                artifactLink == null ? null : artifactLink.scope()
        );
    }

    private static String sourceExtensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        String normalized = fileName.strip();

        int lastDot = normalized.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == normalized.length() - 1) {
            return "";
        }

        return normalized.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private static String rendererAdapterFor(String sourceExtension) {
        return switch (sourceExtension) {
            case "pdf" -> PDF_JS;
            case "doc", "docx" -> "DOCX_PREVIEW";
            case "xls", "xlsx", "csv", "tsv" -> "SHEET_ADAPTER";
            case "ppt", "pptx" -> "SLIDE_ADAPTER";
            case "md", "txt" -> "TEXT_ADAPTER";
            default -> PDF_JS;
        };
    }
}

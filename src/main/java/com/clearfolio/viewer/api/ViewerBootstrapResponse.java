package com.clearfolio.viewer.api;

import java.time.Instant;
import java.util.Locale;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * API payload that initializes the document viewer with lifecycle metadata,
 * renderer selection, and an optional tenant-authorized artifact link.
 *
 * @param docId stable document or conversion-job identifier
 * @param status current conversion lifecycle state
 * @param fileName original client-visible source filename
 * @param viewerMode viewer implementation selected for the rendered artifact
 * @param previewResourcePath resource URL used by the viewer shell
 * @param createdAt time at which the conversion job was accepted
 * @param startedAt time at which processing began, or {@code null}
 * @param completedAt time at which processing completed, or {@code null}
 * @param sourceExtension normalized lowercase source-file extension
 * @param rendererAdapter qualified renderer used to display the converted artifact
 * @param artifactLinkUrl signed artifact URL, or {@code null} when unavailable
 * @param artifactLinkExpiresAt expiry time of the signed artifact URL, or
 *        {@code null}
 * @param artifactLinkScope authorization scope encoded into the artifact link,
 *        or {@code null}
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
                PDF_JS,
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
}

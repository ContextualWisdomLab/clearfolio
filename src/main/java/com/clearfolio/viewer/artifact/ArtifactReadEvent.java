package com.clearfolio.viewer.artifact;

import java.time.Instant;
import java.util.UUID;

/**
 * Runtime audit event for a verified artifact read.
 *
 * @param tenantId tenant that owns the artifact
 * @param subjectId subject encoded in the artifact token
 * @param docId document identifier served by the artifact endpoint
 * @param tokenId artifact token identifier
 * @param rangeRequested optional HTTP range requested by the client
 * @param statusCode response status code returned to the client
 * @param traceId optional caller supplied request trace identifier
 * @param readAt timestamp when the read was recorded
 */
public record ArtifactReadEvent(
        String tenantId,
        String subjectId,
        UUID docId,
        String tokenId,
        String rangeRequested,
        int statusCode,
        String traceId,
        Instant readAt
) {
}

package com.clearfolio.viewer.api;

import java.time.Instant;

/**
 * Buyer-diligence payload for artifact read audit events.
 *
 * @param tenantId tenant that owns the artifact
 * @param subjectId subject encoded in the artifact token
 * @param docId document identifier read through the artifact endpoint
 * @param tokenId artifact token identifier
 * @param rangeRequested optional HTTP range requested by the client
 * @param statusCode response status code returned for the read
 * @param traceId optional caller supplied request trace identifier
 * @param readAt timestamp when the read was recorded
 */
public record ArtifactReadEventResponse(
        String tenantId,
        String subjectId,
        String docId,
        String tokenId,
        String rangeRequested,
        int statusCode,
        String traceId,
        Instant readAt
) {
}

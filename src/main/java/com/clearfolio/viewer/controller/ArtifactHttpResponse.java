package com.clearfolio.viewer.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Shared HTTP response contract for verified PDF artifact delivery.
 *
 * <p>Both canonical viewer artifacts and direct conversion-job downloads use
 * this helper so cache, sniffing, range, and content-range semantics cannot
 * drift between byte-delivery routes. Direct downloads may add attachment and
 * checksum evidence while viewer delivery intentionally omits those headers.</p>
 */
final class ArtifactHttpResponse {

    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String CHECKSUM_HEADER = "X-Checksum-Sha256";

    private ArtifactHttpResponse() {
        // Utility class.
    }

    /**
     * Builds a full PDF response.
     *
     * @param body PDF bytes
     * @param contentDisposition optional attachment disposition
     * @param checksum optional verified SHA-256 checksum
     * @return HTTP 200 artifact response
     */
    static ResponseEntity<byte[]> full(
            byte[] body,
            ContentDisposition contentDisposition,
            String checksum) {
        return decorateRangeHeaders(
                ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF),
                contentDisposition,
                checksum
        ).contentLength(body.length).body(body);
    }

    /**
     * Builds a single-range PDF response.
     *
     * @param body selected PDF bytes
     * @param start inclusive byte start
     * @param end inclusive byte end
     * @param totalLength full artifact size
     * @param contentDisposition optional attachment disposition
     * @param checksum optional verified SHA-256 checksum
     * @return HTTP 206 artifact response
     */
    static ResponseEntity<byte[]> partial(
            byte[] body,
            int start,
            int end,
            int totalLength,
            ContentDisposition contentDisposition,
            String checksum) {
        return decorateRangeHeaders(
                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(
                                HttpHeaders.CONTENT_RANGE,
                                ArtifactHttpRange.RANGE_UNIT_BYTES + " " + start + "-" + end + "/" + totalLength
                        ),
                contentDisposition,
                checksum
        ).contentLength(body.length).body(body);
    }

    /**
     * Builds the controlled failure for malformed or unsatisfiable ranges.
     *
     * @param totalLength full artifact size
     * @param contentDisposition optional attachment disposition
     * @param checksum optional verified SHA-256 checksum
     * @return HTTP 416 response
     */
    static ResponseEntity<byte[]> unsatisfiable(
            int totalLength,
            ContentDisposition contentDisposition,
            String checksum) {
        return decorateRangeHeaders(
                ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, ArtifactHttpRange.RANGE_UNIT_BYTES + " */" + totalLength),
                contentDisposition,
                checksum
        ).build();
    }

    /**
     * Builds a signed-token rejection without range or artifact metadata.
     *
     * @param status token failure status
     * @return controlled token failure response
     */
    static ResponseEntity<byte[]> tokenFailure(HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(CONTENT_TYPE_OPTIONS, "nosniff")
                .build();
    }

    private static ResponseEntity.BodyBuilder decorateRangeHeaders(
            ResponseEntity.BodyBuilder builder,
            ContentDisposition contentDisposition,
            String checksum) {
        builder.header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(CONTENT_TYPE_OPTIONS, "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, ArtifactHttpRange.RANGE_UNIT_BYTES);
        if (contentDisposition != null) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
        }
        if (checksum != null) {
            builder.header(CHECKSUM_HEADER, checksum);
        }
        return builder;
    }
}

package com.clearfolio.viewer.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.ArtifactLinkRequest;
import com.clearfolio.viewer.api.ArtifactLinkResponse;
import com.clearfolio.viewer.api.ConversionJobStatusResponse;
import com.clearfolio.viewer.api.SubmitConversionResponse;
import com.clearfolio.viewer.api.ViewerBootstrapResponse;
import com.clearfolio.viewer.artifact.ArtifactLinkService;
import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.ArtifactTokenClaims;
import com.clearfolio.viewer.artifact.ArtifactTokenException;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.PolicyOverrideRequest;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * HTTP endpoints for submitting conversions and reading conversion results.
 */
@RestController
public class ConversionController {

    /**
     * Header used to identify the operator initiating a dead-letter retry.
     */
    public static final String OPERATOR_ID_HEADER = "X-Clearfolio-Operator-Id";

    private final DocumentConversionService conversionService;
    private final TenantAccessService tenantAccessService;
    private final ArtifactLinkService artifactLinkService;
    private final ArtifactStore artifactStore;
    private final int maxInMemorySizeBytes;

    /**
     * Creates a controller that delegates conversion operations to the service layer.
     *
     * @param conversionService conversion service
     * @param tenantAccessService tenant and permission guard
     * @param artifactLinkService signed artifact link service
     * @param artifactStore artifact store for downloading pdfs
     * @param maxInMemorySize maximum in-memory multipart size
     */
    public ConversionController(
            DocumentConversionService conversionService,
            TenantAccessService tenantAccessService,
            final ArtifactLinkService artifactLinkService,
            ArtifactStore artifactStore,
            @Value("${spring.codec.max-in-memory-size:262144B}") DataSize maxInMemorySize) {
        this.conversionService = conversionService;
        this.tenantAccessService = tenantAccessService;
        this.artifactLinkService = artifactLinkService;
        this.artifactStore = artifactStore;
        long bytes = Math.max(1L, maxInMemorySize.toBytes());
        this.maxInMemorySizeBytes = bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    /**
     * Submits a file for asynchronous conversion.
     *
     * @param file uploaded source file
     * @param policyOverride optional blocked-format override toggle header
     * @param approvalToken optional approval token header used when override is enabled
     * @param approverId optional approver identifier header used when override is enabled
     * @param headers request headers carrying tenant claims
     * @return accepted response containing the job identifier
     */
    @PostMapping(value = "/api/v1/convert/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<SubmitConversionResponse>> submit(
            @RequestPart("file") FilePart file,
            @RequestHeader(value = PolicyOverrideRequest.POLICY_OVERRIDE_HEADER, required = false) String policyOverride,
            @RequestHeader(value = PolicyOverrideRequest.APPROVAL_TOKEN_HEADER, required = false) String approvalToken,
            @RequestHeader(value = PolicyOverrideRequest.APPROVER_ID_HEADER, required = false) String approverId,
            @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.JOB_CREATE);
        PolicyOverrideRequest overrideRequest = PolicyOverrideRequest.of(policyOverride, approvalToken, approverId);
        return DataBufferUtils.join(file.content(), maxInMemorySizeBytes)
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                .publishOn(Schedulers.boundedElastic())
                .map(buffer -> toMultipartFile(file, buffer))
                .map(uploadedFile -> conversionService.submit(uploadedFile, overrideRequest, tenantContext))
                .map(jobId -> ResponseEntity.status(HttpStatus.ACCEPTED).body(SubmitConversionResponse.accepted(jobId)));
    }

    private InMemoryMultipartFile toMultipartFile(FilePart filePart, DataBuffer dataBuffer) {
        byte[] content = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(content);
        DataBufferUtils.release(dataBuffer);

        String contentType = null;
        if (filePart.headers().containsKey(HttpHeaders.CONTENT_TYPE)) {
            contentType = filePart.headers().getContentType() == null
                    ? null
                    : filePart.headers().getContentType().toString();
        }

        return new InMemoryMultipartFile("file", filePart.filename(), contentType, content);
    }

    /**
     * Returns the current status of a conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return conversion status payload
     */
    @GetMapping("/api/v1/convert/jobs/{jobId}")
    public ConversionJobStatusResponse getStatus(@PathVariable UUID jobId, @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.JOB_READ);
        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(tenantContext, job);
        return ConversionJobStatusResponse.from(job);
    }

    /**
     * Retries a dead-lettered conversion job as a new background submission.
     *
     * @param jobId conversion job identifier
     * @param operatorId operator identifier header value
     * @param headers request headers carrying tenant claims
     * @return accepted response containing the retried job identifier
     */
    @PostMapping("/api/v1/convert/jobs/{jobId}/retry")
    public ResponseEntity<SubmitConversionResponse> retryDeadLettered(
            @PathVariable UUID jobId,
            @RequestHeader(value = OPERATOR_ID_HEADER, required = false) String operatorId,
            @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.JOB_RETRY);
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException(OPERATOR_ID_HEADER + " header is required.");
        }

        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(tenantContext, job);
        RetryDeadLetterResult retryResult = conversionService.retryDeadLettered(jobId, operatorId.strip());
        if (retryResult == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only dead-lettered failed jobs can be retried");
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(SubmitConversionResponse.accepted(jobId));
    }

    /**
     * Deletes a conversion job and its associated generated artifacts.
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return no content on success
     */
    @DeleteMapping("/api/v1/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID jobId, @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.JOB_DELETE);
        if (!conversionService.deleteJob(jobId, tenantContext)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Returns viewer bootstrap data once conversion output is ready.
     *
     * @param docId document identifier
     * @param headers request headers carrying tenant claims
     * @return viewer bootstrap payload for a converted document
     */
    @GetMapping({"/api/v1/viewer/{docId}", "/api/v1/convert/viewer/{docId}"})
    public ViewerBootstrapResponse getViewer(
            @PathVariable("docId") UUID docId,
            @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.VIEWER_READ);
        return getViewerBootstrap(docId, tenantContext);
    }

    /**
     * Downloads the converted PDF artifact for a tenant-owned conversion job.
     *
     * <p>The caller must have the dedicated artifact-read permission and a valid
     * signed artifact token. Job-status read access or tenant ownership alone does
     * not authorize access to document bytes. The endpoint supports zero or one
     * HTTP byte range and records verified read evidence.</p>
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @param rangeHeader optional {@code Range} header
     * @param queryToken signed artifact token query parameter
     * @param authorizationHeader optional bearer artifact token
     * @param traceId optional request trace identifier
     * @return signed PDF bytes with attachment disposition and checksum evidence
     */
    @GetMapping("/api/v1/convert/jobs/{jobId}/download")
    public Mono<ResponseEntity<byte[]>> downloadArtifact(
            @PathVariable UUID jobId,
            @RequestHeader HttpHeaders headers,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestParam(value = ArtifactLinkService.ARTIFACT_TOKEN_PARAM, required = false) String queryToken,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = "X-Request-Id", required = false) String traceId) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.ARTIFACT_READ);
        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(tenantContext, job);

        if (job.getStatus() != ConversionJobStatus.SUCCEEDED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    job.getStatus() + " not ready yet. retry in a few seconds"
            );
        }

        Optional<byte[]> stored = artifactStore.getPdf(jobId);
        if (stored.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found");
        }

        byte[] pdfBytes = stored.get();
        String token = ArtifactLinkService.resolveToken(queryToken, authorizationHeader);
        ArtifactTokenClaims claims;
        try {
            claims = artifactLinkService.verifyReadToken(jobId, job, pdfBytes, token);
        } catch (ArtifactTokenException ex) {
            return Mono.just(ArtifactHttpResponse.tokenFailure(ex.getStatus()));
        }

        String checksum = claims.artifactChecksum();
        String filename = pdfDownloadFilename(job.getOriginalFileName());
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename)
                .build();
        int totalLength = pdfBytes.length;
        Optional<ArtifactHttpRange.ResolvedRange> range = ArtifactHttpRange.resolveSingleRange(rangeHeader, totalLength);

        if (range.isPresent() && range.get().rejected()) {
            ResponseEntity<byte[]> response = ArtifactHttpResponse.unsatisfiable(
                    totalLength,
                    contentDisposition,
                    checksum
            );
            artifactLinkService.recordRead(claims, rangeHeader, response.getStatusCode().value(), traceId);
            return Mono.just(response);
        }

        if (range.isEmpty()) {
            ResponseEntity<byte[]> response = ArtifactHttpResponse.full(pdfBytes, contentDisposition, checksum);
            artifactLinkService.recordRead(claims, rangeHeader, response.getStatusCode().value(), traceId);
            return Mono.just(response);
        }

        ArtifactHttpRange.ResolvedRange resolved = range.get();
        int start = resolved.startInclusive();
        int end = resolved.endInclusive();
        byte[] slice = java.util.Arrays.copyOfRange(pdfBytes, start, end + 1);
        ResponseEntity<byte[]> response = ArtifactHttpResponse.partial(
                slice,
                start,
                end,
                totalLength,
                contentDisposition,
                checksum
        );
        artifactLinkService.recordRead(claims, rangeHeader, response.getStatusCode().value(), traceId);
        return Mono.just(response);
    }

    static String pdfDownloadFilename(String originalFileName) {
        String baseName = "document";
        if (originalFileName != null && !originalFileName.isBlank()) {
            baseName = originalFileName.strip();
            int lastDotIndex = baseName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                baseName = baseName.substring(0, lastDotIndex);
            }
        }

        String sanitized = sanitizeFilenameBase(baseName);
        if (sanitized.chars().allMatch(character -> character == '.' || character == '_')) {
            sanitized = "document";
        }
        return sanitized + ".pdf";
    }

    private static String sanitizeFilenameBase(String baseName) {
        int firstBad = -1;
        for (int index = 0; index < baseName.length(); index++) {
            char character = baseName.charAt(index);
            if (!(Character.isLetterOrDigit(character)
                    || character == '.'
                    || character == '-'
                    || character == '_')) {
                firstBad = index;
                break;
            }
        }

        if (firstBad == -1) {
            return baseName;
        }

        StringBuilder sanitized = new StringBuilder(baseName.length());
        sanitized.append(baseName, 0, firstBad);
        for (int index = firstBad; index < baseName.length(); index++) {
            char character = baseName.charAt(index);
            if (Character.isLetterOrDigit(character)
                    || character == '.'
                    || character == '-'
                    || character == '_') {
                sanitized.append(character);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }

    private ViewerBootstrapResponse getViewerBootstrap(UUID docId, TenantContext tenantContext) {
        ConversionJob job = conversionService.getJob(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        tenantAccessService.requireSameTenant(tenantContext, job);

        if (job.getStatus() == ConversionJobStatus.SUCCEEDED) {
            ArtifactLinkResponse artifactLink = artifactLinkService.createLink(
                    job,
                    tenantContext,
                    ArtifactLinkRequest.viewerPreview()
            );
            return ViewerBootstrapResponse.from(job, artifactLink);
        }

        if (job.getStatus() == ConversionJobStatus.FAILED) {
            String statusLabel = job.isDeadLettered() ? "DEAD_LETTERED" : "FAILED";
            throw new ResponseStatusException(HttpStatus.CONFLICT, statusLabel + ": " + job.getStatusMessage());
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                job.getStatus() + " not ready yet. retry in a few seconds"
        );
    }
}

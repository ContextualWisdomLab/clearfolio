package com.clearfolio.viewer.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.util.unit.DataSize;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.api.ArtifactLinkRequest;
import com.clearfolio.viewer.api.ArtifactLinkResponse;
import com.clearfolio.viewer.api.ConversionJobStatusResponse;
import com.clearfolio.viewer.api.SubmitConversionResponse;
import com.clearfolio.viewer.api.ViewerBootstrapResponse;
import com.clearfolio.viewer.artifact.ArtifactLinkService;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.PolicyOverrideRequest;
import com.clearfolio.viewer.service.RetryDeadLetterResult;
import com.clearfolio.viewer.artifact.ArtifactStore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

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
            ArtifactLinkService artifactLinkService,
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
     * Downloads the converted PDF artifact.
     *
     * @param jobId conversion job identifier
     * @return PDF bytes with attachment disposition and checksum header
     */
    @GetMapping("/api/v1/convert/jobs/{jobId}/download")
    public Mono<ResponseEntity<byte[]>> downloadArtifact(@PathVariable UUID jobId) {
        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));

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
        String checksum = calculateSha256(pdfBytes);
        String filename = pdfDownloadFilename(job.getOriginalFileName());
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename)
                .build();

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Checksum-Sha256", checksum)
                .body(pdfBytes));
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
        if (sanitized.isBlank() || sanitized.chars().allMatch(character -> character == '.' || character == '_')) {
            sanitized = "document";
        }
        return sanitized + ".pdf";
    }

    private static String sanitizeFilenameBase(String baseName) {
        StringBuilder sanitized = new StringBuilder(baseName.length());
        for (int index = 0; index < baseName.length(); index++) {
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

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
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

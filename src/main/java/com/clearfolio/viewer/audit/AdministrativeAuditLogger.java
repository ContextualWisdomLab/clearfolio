package com.clearfolio.viewer.audit;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.security.AuditPseudonymizer;

/**
 * Emits structured, privacy-safe administrative authorization evidence.
 *
 * <p>Actor and tenant identifiers are pseudonymized in separate HMAC domains.
 * The logger never emits raw claim headers, subject identifiers, tenant
 * identifiers, tokens, filenames, job messages, or document content.</p>
 */
@Component
public final class AdministrativeAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdministrativeAuditLogger.class);
    private static final String NO_JOB_ID = "none";
    private static final int NO_RESULT_COUNT = -1;

    private final AuditPseudonymizer actorPseudonymizer;
    private final AuditPseudonymizer tenantPseudonymizer;

    /**
     * Administrative actions represented in authorization evidence.
     */
    public enum Action {
        /** Lists tenant-owned conversion jobs. */
        LIST_JOBS,
        /** Deletes one tenant-owned conversion job. */
        DELETE_JOB,
        /** Retries one tenant-owned dead-lettered conversion job. */
        RETRY_JOB
    }

    /**
     * Stable outcomes represented in authorization evidence.
     */
    public enum Outcome {
        /** Authorization and the requested operation succeeded. */
        ALLOWED,
        /** Authentication or permission evaluation denied the request. */
        DENIED,
        /** The resource was absent or intentionally concealed. */
        NOT_FOUND,
        /** The resource existed but its state rejected the operation. */
        NOT_ELIGIBLE,
        /** Authorization succeeded but the operation failed unexpectedly. */
        FAILED
    }

    /**
     * Creates the logger from the dedicated audit pseudonym configuration.
     *
     * @param properties conversion and audit configuration
     */
    public AdministrativeAuditLogger(ConversionProperties properties) {
        this.actorPseudonymizer = AuditPseudonymizer.forAdministrativeActor(
                properties.getAuditPseudonymSecret(),
                properties.getAuditPseudonymKeyVersion()
        );
        this.tenantPseudonymizer = AuditPseudonymizer.forAdministrativeTenant(
                properties.getAuditPseudonymSecret(),
                properties.getAuditPseudonymKeyVersion()
        );
    }

    /**
     * Returns a privacy-safe actor identifier suitable for retry provenance.
     *
     * @param context authenticated tenant context, or null when unavailable
     * @return administrative actor fingerprint
     */
    public String actorFingerprint(TenantContext context) {
        return actorPseudonymizer.fingerprint(context == null ? null : context.subjectId());
    }

    /**
     * Records an authorization decision using authenticated context values.
     *
     * @param context authenticated tenant context, or null when unavailable
     * @param action administrative action
     * @param outcome decision outcome
     * @param status response status
     * @param jobId optional opaque job identifier
     * @param resultCount optional list result count
     */
    public void record(
            TenantContext context,
            Action action,
            Outcome outcome,
            HttpStatusCode status,
            UUID jobId,
            Integer resultCount
    ) {
        recordIdentifiers(
                context == null ? null : context.tenantId(),
                context == null ? null : context.subjectId(),
                action,
                outcome,
                status,
                jobId,
                resultCount
        );
    }

    /**
     * Records a denied request using untrusted headers only as HMAC inputs.
     *
     * @param headers request headers, or null when unavailable
     * @param action administrative action
     * @param outcome decision outcome
     * @param status response status
     * @param jobId optional opaque job identifier
     */
    public void recordHeaders(
            HttpHeaders headers,
            Action action,
            Outcome outcome,
            HttpStatusCode status,
            UUID jobId
    ) {
        recordIdentifiers(
                firstHeader(headers, TenantContext.TENANT_ID_HEADER),
                firstHeader(headers, TenantContext.SUBJECT_ID_HEADER),
                action,
                outcome,
                status,
                jobId,
                null
        );
    }

    private void recordIdentifiers(
            String tenantId,
            String subjectId,
            Action action,
            Outcome outcome,
            HttpStatusCode status,
            UUID jobId,
            Integer resultCount
    ) {
        LOGGER.info(
                "Administrative access decision action={} outcome={} status={} "
                        + "tenantFingerprint={} actorFingerprint={} jobId={} resultCount={}",
                action,
                outcome,
                status.value(),
                tenantPseudonymizer.fingerprint(tenantId),
                actorPseudonymizer.fingerprint(subjectId),
                jobId == null ? NO_JOB_ID : jobId,
                resultCount == null ? NO_RESULT_COUNT : resultCount
        );
    }

    private String firstHeader(HttpHeaders headers, String name) {
        return headers == null ? null : headers.getFirst(name);
    }
}

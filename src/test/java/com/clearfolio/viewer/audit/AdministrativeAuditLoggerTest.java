package com.clearfolio.viewer.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.clearfolio.viewer.audit.AdministrativeAuditLogger.Action;
import com.clearfolio.viewer.audit.AdministrativeAuditLogger.Outcome;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.security.AuditPseudonymizer;

class AdministrativeAuditLoggerTest {

    private static final String AUDIT_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void recordsAuthenticatedContextWithoutRawIdentifiers() {
        AdministrativeAuditLogger auditLogger = configuredLogger(AUDIT_SECRET, "rotation-8");
        TenantContext context = new TenantContext(
                "sensitive-tenant",
                "employee-007@example.com",
                Set.of("admin:read")
        );
        CapturingAppender appender = attachAppender();

        try {
            auditLogger.record(
                    context,
                    Action.LIST_JOBS,
                    Outcome.ALLOWED,
                    HttpStatus.OK,
                    null,
                    2
            );
        } finally {
            appender.closeAndDetach();
        }

        String message = appender.singleMessage();
        assertTrue(message.contains("action=LIST_JOBS"));
        assertTrue(message.contains("outcome=ALLOWED"));
        assertTrue(message.contains("status=200"));
        assertTrue(message.contains("jobFingerprint=absent:rotation-8"));
        assertFalse(message.contains("jobId="));
        assertTrue(message.contains("resultCount=2"));
        assertTrue(message.matches(".*tenantFingerprint=rotation-8:[0-9a-f]{32}.*"));
        assertTrue(message.matches(".*actorFingerprint=rotation-8:[0-9a-f]{32}.*"));
        assertFalse(message.contains("sensitive-tenant"));
        assertFalse(message.contains("employee-007@example.com"));
        assertEquals("absent:rotation-8", auditLogger.actorFingerprint(null));
        assertNotEquals(
                auditLogger.actorFingerprint(context),
                fieldValue(message, "tenantFingerprint")
        );
    }

    @Test
    void recordsUntrustedHeadersAndJobIdentifiersOnlyAsDomainSeparatedPseudonyms() {
        AdministrativeAuditLogger auditLogger = configuredLogger(AUDIT_SECRET, "v2");
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantContext.TENANT_ID_HEADER, "tenant-from-untrusted-header");
        headers.set(TenantContext.SUBJECT_ID_HEADER, "subject-from-untrusted-header");
        UUID jobId = UUID.randomUUID();
        CapturingAppender appender = attachAppender();

        try {
            auditLogger.recordHeaders(
                    headers,
                    Action.DELETE_JOB,
                    Outcome.DENIED,
                    HttpStatus.FORBIDDEN,
                    jobId
            );
        } finally {
            appender.closeAndDetach();
        }

        String message = appender.singleMessage();
        assertTrue(message.contains("action=DELETE_JOB"));
        assertTrue(message.contains("outcome=DENIED"));
        assertTrue(message.contains("status=403"));
        assertTrue(message.matches(".*jobFingerprint=v2:[0-9a-f]{32}.*"));
        assertFalse(message.contains(jobId.toString()));
        assertFalse(message.contains("jobId="));
        assertTrue(message.contains("resultCount=-1"));
        assertFalse(message.contains("tenant-from-untrusted-header"));
        assertFalse(message.contains("subject-from-untrusted-header"));

        String tenantFingerprint = fieldValue(message, "tenantFingerprint");
        String actorFingerprint = fieldValue(message, "actorFingerprint");
        String jobFingerprint = fieldValue(message, "jobFingerprint");
        assertNotEquals(tenantFingerprint, actorFingerprint);
        assertNotEquals(tenantFingerprint, jobFingerprint);
        assertNotEquals(actorFingerprint, jobFingerprint);
    }

    @Test
    void separatesAdministrativeDomainsForIdenticalIdentifierValues() {
        String sharedIdentifier = "11111111-1111-1111-1111-111111111111";
        String actorFingerprint = AuditPseudonymizer
                .forAdministrativeActor(AUDIT_SECRET, "v2")
                .fingerprint(sharedIdentifier);
        String tenantFingerprint = AuditPseudonymizer
                .forAdministrativeTenant(AUDIT_SECRET, "v2")
                .fingerprint(sharedIdentifier);
        String jobFingerprint = AuditPseudonymizer
                .forAdministrativeJob(AUDIT_SECRET, "v2")
                .fingerprint(sharedIdentifier);

        assertNotEquals(actorFingerprint, tenantFingerprint);
        assertNotEquals(actorFingerprint, jobFingerprint);
        assertNotEquals(tenantFingerprint, jobFingerprint);
    }

    @Test
    void usesExplicitAbsentAndUnavailableMarkers() {
        AdministrativeAuditLogger unavailableLogger = configuredLogger("", "v9");
        HttpHeaders presentHeaders = new HttpHeaders();
        presentHeaders.set(TenantContext.TENANT_ID_HEADER, "tenant");
        presentHeaders.set(TenantContext.SUBJECT_ID_HEADER, "subject");
        CapturingAppender appender = attachAppender();

        try {
            unavailableLogger.recordHeaders(
                    presentHeaders,
                    Action.RETRY_JOB,
                    Outcome.DENIED,
                    HttpStatus.UNAUTHORIZED,
                    null
            );
            unavailableLogger.recordHeaders(
                    null,
                    Action.RETRY_JOB,
                    Outcome.DENIED,
                    HttpStatus.UNAUTHORIZED,
                    null
            );
            unavailableLogger.record(
                    null,
                    Action.RETRY_JOB,
                    Outcome.FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    null,
                    null
            );
        } finally {
            appender.closeAndDetach();
        }

        List<String> messages = appender.messages();
        assertEquals(3, messages.size());
        assertTrue(messages.get(0).contains("tenantFingerprint=unavailable:v9"));
        assertTrue(messages.get(0).contains("actorFingerprint=unavailable:v9"));
        assertTrue(messages.get(0).contains("jobFingerprint=absent:v9"));
        assertTrue(messages.get(1).contains("tenantFingerprint=absent:v9"));
        assertTrue(messages.get(1).contains("actorFingerprint=absent:v9"));
        assertTrue(messages.get(1).contains("jobFingerprint=absent:v9"));
        assertTrue(messages.get(2).contains("tenantFingerprint=absent:v9"));
        assertTrue(messages.get(2).contains("actorFingerprint=absent:v9"));
        assertTrue(messages.get(2).contains("jobFingerprint=absent:v9"));
    }

    private static String fieldValue(String message, String fieldName) {
        String prefix = fieldName + "=";
        int start = message.indexOf(prefix);
        assertTrue(start >= 0, "missing field " + fieldName + " in: " + message);
        int end = message.indexOf(' ', start);
        return message.substring(start + prefix.length(), end < 0 ? message.length() : end);
    }

    private static AdministrativeAuditLogger configuredLogger(String secret, String version) {
        ConversionProperties properties = new ConversionProperties();
        properties.setAuditPseudonymSecret(secret);
        properties.setAuditPseudonymKeyVersion(version);
        return new AdministrativeAuditLogger(properties);
    }

    private static CapturingAppender attachAppender() {
        Logger logger = (Logger) LogManager.getLogger(AdministrativeAuditLogger.class);
        CapturingAppender appender = new CapturingAppender(logger, logger.getLevel());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return appender;
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final Logger logger;
        private final Level previousLevel;
        private final List<String> messages = new ArrayList<>();

        private CapturingAppender(Logger logger, Level previousLevel) {
            super(
                    "administrative-audit-test-appender",
                    null,
                    PatternLayout.newBuilder().withPattern("%m").build(),
                    false,
                    null
            );
            this.logger = logger;
            this.previousLevel = previousLevel;
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        private String singleMessage() {
            assertEquals(1, messages.size());
            return messages.getFirst();
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }

        private void closeAndDetach() {
            logger.removeAppender(this);
            logger.setLevel(previousLevel);
            stop();
        }
    }
}

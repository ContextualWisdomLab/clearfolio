package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.clearfolio.viewer.config.ConversionProperties;

class DefaultDocumentValidationServiceAuditTest {

    private static final String AUDIT_PSEUDONYM_SECRET =
            "0123456789abcdef0123456789abcdef";

    @Test
    void acceptedOverrideLogsOnlyPrivacySafeFingerprints() {
        String approverId = "employee-007@example.com";
        String policySecret = "policy-signing-secret";
        String approvalToken = generateSignature(approverId, "hwp", policySecret);
        ConversionProperties properties = configuredProperties(
                policySecret,
                AUDIT_PSEUDONYM_SECRET,
                "rotation-7"
        );
        DefaultDocumentValidationService service = new DefaultDocumentValidationService(properties);
        CapturingAppender appender = attachAppender();

        try {
            service.validateOrThrow(
                    new MockMultipartFile(
                            "file",
                            "contract.hwp",
                            "application/octet-stream",
                            new byte[] {1}
                    ),
                    PolicyOverrideRequest.of("true", approvalToken, approverId)
            );
        } finally {
            appender.closeAndDetach();
        }

        String auditLine = appender.singleMessage();
        assertTrue(auditLine.contains("approverFingerprint=rotation-7:"));
        assertTrue(auditLine.contains("tokenFingerprint="));
        assertFalse(auditLine.contains(approverId));
        assertFalse(auditLine.contains(approvalToken));
        assertFalse(auditLine.contains("approverId="));
    }

    @Test
    void missingDedicatedAuditKeyUsesNonCorrelatableSentinel() {
        String approverId = "employee-008";
        String policySecret = "policy-signing-secret";
        String approvalToken = generateSignature(approverId, "hwp", policySecret);
        ConversionProperties properties = configuredProperties(policySecret, "", "v9");
        DefaultDocumentValidationService service = new DefaultDocumentValidationService(properties);
        CapturingAppender appender = attachAppender();

        try {
            service.validateOrThrow(
                    new MockMultipartFile(
                            "file",
                            "contract.hwp",
                            "application/octet-stream",
                            new byte[] {1}
                    ),
                    PolicyOverrideRequest.of("true", approvalToken, approverId)
            );
        } finally {
            appender.closeAndDetach();
        }

        String auditLine = appender.singleMessage();
        assertTrue(auditLine.contains("approverFingerprint=unavailable:v9"));
        assertFalse(auditLine.contains(approverId));
        assertFalse(auditLine.contains(approvalToken));
    }

    @Test
    void auditConfigurationNullsUseDocumentedSafeDefaults() {
        ConversionProperties properties = new ConversionProperties();

        properties.setAuditPseudonymSecret(null);
        properties.setAuditPseudonymKeyVersion(null);

        assertEquals("", properties.getAuditPseudonymSecret());
        assertEquals("v1", properties.getAuditPseudonymKeyVersion());
    }

    private static ConversionProperties configuredProperties(
            String policySecret,
            String auditSecret,
            String keyVersion) {
        ConversionProperties properties = new ConversionProperties();
        properties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        properties.setPolicyOverrideSecret(policySecret);
        properties.setAuditPseudonymSecret(auditSecret);
        properties.setAuditPseudonymKeyVersion(keyVersion);
        return properties;
    }

    private static String generateSignature(String approverId, String extension, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = approverId.length() + ":" + approverId + extension;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("test signature generation failed", ex);
        }
    }

    private static CapturingAppender attachAppender() {
        Logger logger = (Logger) LogManager.getLogger(DefaultDocumentValidationService.class);
        CapturingAppender appender = new CapturingAppender(logger);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return appender;
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final Logger logger;
        private final List<String> messages = new ArrayList<>();

        private CapturingAppender(Logger logger) {
            super(
                    "audit-test-appender",
                    null,
                    PatternLayout.newBuilder().withPattern("%m").build(),
                    false,
                    null
            );
            this.logger = logger;
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        private String singleMessage() {
            assertEquals(1, messages.size());
            return messages.getFirst();
        }

        private void closeAndDetach() {
            logger.removeAppender(this);
            stop();
        }
    }
}

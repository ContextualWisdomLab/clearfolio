package com.clearfolio.viewer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;

/**
 * Default document validator that enforces extension and size constraints.
 */
@Service
public class DefaultDocumentValidationService implements DocumentValidationService {

    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDocumentValidationService.class);
    private static final int FINGERPRINT_TRUNCATE_BYTES = 8;

    private final Set<String> blockedExtensions;
    private final long maxUploadSizeBytes;
    private final String policyOverrideSecret;

    /**
     * Creates the validation service from conversion configuration values.
     *
     * @param conversionProperties conversion configuration values
     */
    public DefaultDocumentValidationService(ConversionProperties conversionProperties) {
        this.blockedExtensions = conversionProperties.getBlockedExtensions();
        this.maxUploadSizeBytes = conversionProperties.getMaxUploadSizeBytes();
        this.policyOverrideSecret = conversionProperties.getPolicyOverrideSecret();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateOrThrow(MultipartFile file) {
        validateOrThrow(file, PolicyOverrideRequest.none());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateOrThrow(MultipartFile file, PolicyOverrideRequest overrideRequest) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }

        String fileName = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (extension.isEmpty()) {
            throw new IllegalArgumentException("File extension is required.");
        }

        boolean blockedExtension = blockedExtensions.contains(extension);
        String overrideApproverIdForAudit = null;
        String overrideTokenForAudit = null;
        if (blockedExtension) {
            PolicyOverrideRequest effectiveOverride = overrideRequest == null
                    ? PolicyOverrideRequest.none()
                    : overrideRequest;
            if (!isPolicyOverrideEnabled(effectiveOverride.policyOverride())) {
                throw new UnsupportedDocumentFormatException(extension);
            }

            String approvalToken = requireNonBlank(
                    effectiveOverride.approvalToken(),
                    PolicyOverrideRequest.APPROVAL_TOKEN_HEADER + " is required when policy override is true."
            );
            String approverId = requireNonBlank(
                    effectiveOverride.approverId(),
                    PolicyOverrideRequest.APPROVER_ID_HEADER + " is required when policy override is true."
            );

            if (policyOverrideSecret == null || policyOverrideSecret.isBlank()) {
                throw new IllegalStateException("Policy override secret is not configured.");
            }

            byte[] providedBytes;
            try {
                providedBytes = HexFormat.of().parseHex(approvalToken);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid policy override signature.", e);
            }

            byte[] expectedBytes = computeSignature(approverId, extension, policyOverrideSecret);
            if (!MessageDigest.isEqual(providedBytes, expectedBytes)) {
                throw new IllegalArgumentException("Invalid policy override signature.");
            }

            overrideApproverIdForAudit = approverId;
            overrideTokenForAudit = approvalToken;
        }

        if (file.getSize() > maxUploadSizeBytes) {
            throw new IllegalArgumentException("File is too large.");
        }

        if (blockedExtension) {
            LOGGER.info(
                    "Blocked-format override accepted extension={} approverId={} tokenFingerprint={}",
                    sanitizeForLog(extension),
                    fingerprintApproverId(overrideApproverIdForAudit),
                    tokenFingerprint(overrideTokenForAudit)
            );
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        if (filename.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("File name contains null byte.");
        }
        String cleanPath = org.springframework.util.StringUtils.cleanPath(filename);
        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash != -1) {
            return cleanPath.substring(lastSlash + 1);
        }
        return cleanPath;
    }

    private String extensionOf(final String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        if (fileName.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("File name contains null byte.");
        }

        java.nio.file.Path leafName = java.nio.file.Path.of(fileName.strip()).getFileName();
        if (leafName == null) {
            return "";
        }
        String normalized = leafName.toString();
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == normalized.length() - 1) {
            return "";
        }

        String extension = normalized.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        if (extension.contains(":")) {
            throw new IllegalArgumentException("File extension is invalid.");
        }
        return extension;
    }

    private boolean isPolicyOverrideEnabled(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return false;
        }

        String normalized = headerValue.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }

        throw new IllegalArgumentException(
                PolicyOverrideRequest.POLICY_OVERRIDE_HEADER + " must be true or false."
        );
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private byte[] computeSignature(String approverId, String extension, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = approverId.length() + ":" + approverId + extension;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable or key invalid", ex);
        }
    }

    private String tokenFingerprint(String approvalToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(approvalToken.getBytes(StandardCharsets.UTF_8));
            // Reused HexFormat for performance
            return HEX_FORMAT.formatHex(hashed, 0, FINGERPRINT_TRUNCATE_BYTES);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    private String fingerprintApproverId(String approverId) {
        if (approverId == null || approverId.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(approverId.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    private String sanitizeForLog(final String value) {
        if (value == null) {
            return "";
        }
        // ⚡ Bolt: Single-pass string sanitization
        // Avoids multiple allocations from chained replace() calls.
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean needsReplace = c == '\u0000' || c == '\t' || c == '\r'
                    || c == '\n' || (c >= '\u2028' && c <= '\u202E');
            if (needsReplace) {
                if (sb == null) {
                    sb = new StringBuilder(value.length());
                    sb.append(value, 0, i);
                }
                sb.append('_');
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? value : sb.toString();
    }
}

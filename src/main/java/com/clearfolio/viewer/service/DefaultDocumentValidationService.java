package com.clearfolio.viewer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Base64;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDocumentValidationService.class);
    private static final int FINGERPRINT_TRUNCATE_BYTES = 8;

    private final Set<String> blockedExtensions;
    private final long maxUploadSizeBytes;
    private final String policyOverridePublicKey;

    /**
     * Creates the validation service from conversion configuration values.
     *
     * @param conversionProperties conversion configuration values
     */
    public DefaultDocumentValidationService(ConversionProperties conversionProperties) {
        this.blockedExtensions = conversionProperties.getBlockedExtensions();
        this.maxUploadSizeBytes = conversionProperties.getMaxUploadSizeBytes();
        this.policyOverridePublicKey = conversionProperties.getPolicyOverridePublicKey();
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

        String fileName = file.getOriginalFilename();
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
            verifySignature(approvalToken, approverId, extension);
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
                    sanitizeForLog(overrideApproverIdForAudit),
                    tokenFingerprint(overrideTokenForAudit)
            );
        }
    }

    private String extensionOf(String fileName) {
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



    private void verifySignature(String token, String approverId, String extension) {
        if (policyOverridePublicKey == null || policyOverridePublicKey.isEmpty()) {
            throw new IllegalArgumentException("Policy override public key is not configured.");
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(policyOverridePublicKey.trim());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            String payload = approverId + ":" + extension;
            sig.update(payload.getBytes(StandardCharsets.UTF_8));

            byte[] tokenBytes = HexFormat.of().parseHex(token);
            if (!sig.verify(tokenBytes)) {
                throw new IllegalArgumentException("Invalid policy override signature.");
            }
        } catch (IllegalArgumentException ex) {
            if ("Invalid policy override signature.".equals(ex.getMessage())) {
                throw ex;
            }
            throw new IllegalArgumentException("Invalid policy override signature or public key.", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("RSA SHA-256 unavailable or key invalid", ex);
        }
    }
    private String tokenFingerprint(String approvalToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(approvalToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed, 0, FINGERPRINT_TRUNCATE_BYTES);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u0000', '_')
                .replace('\t', '_')
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\u2028', '_')
                .replace('\u2029', '_')
                .replace('\u202A', '_')
                .replace('\u202B', '_')
                .replace('\u202C', '_')
                .replace('\u202D', '_')
                .replace('\u202E', '_');
    }
}

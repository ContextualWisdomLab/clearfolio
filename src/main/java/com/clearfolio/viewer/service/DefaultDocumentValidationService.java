package com.clearfolio.viewer.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * Default document validator that enforces extension, size, and content signature constraints.
 */
@Service
public class DefaultDocumentValidationService implements DocumentValidationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDocumentValidationService.class);
    private static final int FINGERPRINT_TRUNCATE_BYTES = 8;
    private static final int SIGNATURE_BYTES = 8;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_LOCAL_FILE_SIGNATURE = new byte[] {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] ZIP_EMPTY_ARCHIVE_SIGNATURE = new byte[] {0x50, 0x4B, 0x05, 0x06};
    private static final byte[] ZIP_SPANNED_ARCHIVE_SIGNATURE = new byte[] {0x50, 0x4B, 0x07, 0x08};
    private static final byte[] OLE_COMPOUND_FILE_SIGNATURE = new byte[] {
        (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final byte[] RTF_SIGNATURE = "{\\rtf".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> ZIP_DOCUMENT_EXTENSIONS = Set.of("docx", "pptx", "xlsx", "odt", "odp", "ods");
    private static final Set<String> OLE_DOCUMENT_EXTENSIONS = Set.of("doc", "ppt", "xls");
    private static final Set<String> TEXT_DOCUMENT_EXTENSIONS = Set.of("csv", "md", "txt");

    private final Set<String> blockedExtensions;
    private final long maxUploadSizeBytes;

    /**
     * Creates the validation service from conversion configuration values.
     *
     * @param conversionProperties conversion configuration values
     */
    public DefaultDocumentValidationService(ConversionProperties conversionProperties) {
        this.blockedExtensions = conversionProperties.getBlockedExtensions();
        this.maxUploadSizeBytes = conversionProperties.getMaxUploadSizeBytes();
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
        if (fileName != null && fileName.indexOf('\0') != -1) {
            throw new IllegalArgumentException("File name is invalid.");
        }

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
            overrideApproverIdForAudit = approverId;
            overrideTokenForAudit = approvalToken;
        }

        if (file.getSize() > maxUploadSizeBytes) {
            throw new IllegalArgumentException("File is too large.");
        }

        if (!blockedExtension) {
            validateContentSignature(file, extension);
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

    private void validateContentSignature(MultipartFile file, String extension) {
        byte[] header = readHeader(file);
        boolean valid = switch (extension) {
            case "pdf" -> startsWith(header, PDF_SIGNATURE);
            case "rtf" -> startsWith(header, RTF_SIGNATURE);
            default -> {
                if (ZIP_DOCUMENT_EXTENSIONS.contains(extension)) {
                    yield startsWith(header, ZIP_LOCAL_FILE_SIGNATURE)
                            || startsWith(header, ZIP_EMPTY_ARCHIVE_SIGNATURE)
                            || startsWith(header, ZIP_SPANNED_ARCHIVE_SIGNATURE);
                }
                if (OLE_DOCUMENT_EXTENSIONS.contains(extension)) {
                    yield startsWith(header, OLE_COMPOUND_FILE_SIGNATURE);
                }
                if (TEXT_DOCUMENT_EXTENSIONS.contains(extension)) {
                    yield isTextLike(header);
                }
                throw new IllegalArgumentException("File extension is not supported.");
            }
        };

        if (!valid) {
            throw new IllegalArgumentException("File content does not match file extension.");
        }
    }

    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[SIGNATURE_BYTES];
        try (InputStream inputStream = file.getInputStream()) {
            int bytesRead = inputStream.readNBytes(header, 0, header.length);
            if (bytesRead == header.length) {
                return header;
            }

            byte[] truncated = new byte[bytesRead];
            System.arraycopy(header, 0, truncated, 0, bytesRead);
            return truncated;
        } catch (IOException ex) {
            throw new IllegalArgumentException("File content could not be read.", ex);
        }
    }

    private boolean startsWith(byte[] actual, byte[] expected) {
        if (actual.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (actual[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isTextLike(byte[] header) {
        for (byte value : header) {
            int unsigned = Byte.toUnsignedInt(value);
            if (unsigned == 0 || unsigned < 0x09 || (unsigned > 0x0D && unsigned < 0x20)) {
                return false;
            }
        }
        return true;
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

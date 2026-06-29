package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;

class DefaultDocumentValidationServiceTest {

    private static final Object SECURITY_PROVIDERS_LOCK = new Object();

    @Test
    void rejectsHwpAndHwpxByDefault() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("hwp", ex.getExtension());
    }

    @Test
    void allowsBlockedExtensionWhenOverrideHeadersAreValid() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertDoesNotThrow(() -> validationService.validateOrThrow(
                new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                PolicyOverrideRequest.of("true", "token-123", "approver-1")
        ));
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideFlagIsInvalid() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of("not-boolean", "token-123", "approver-1")
                )
        );

        assertEquals("X-Clearfolio-Policy-Override must be true or false.", ex.getMessage());
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideTokenIsMissing() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of("true", " ", "approver-1")
                )
        );

        assertEquals("X-Clearfolio-Approval-Token is required when policy override is true.", ex.getMessage());
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideTokenIsNull() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of("true", null, "approver-1")
                )
        );

        assertEquals("X-Clearfolio-Approval-Token is required when policy override is true.", ex.getMessage());
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideApproverIsMissing() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of("true", "token-123", " ")
                )
        );

        assertEquals("X-Clearfolio-Approver-Id is required when policy override is true.", ex.getMessage());
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideFlagIsFalse() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of("false", "token-123", "approver-1")
                )
        );

        assertEquals("hwp", ex.getExtension());
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideFlagIsBlank() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of(" ", "token-123", "approver-1")
                )
        );

        assertEquals("hwp", ex.getExtension());
    }

    @Test
    void ignoresInvalidOverrideFlagForSupportedExtension() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertDoesNotThrow(() -> validationService.validateOrThrow(
                new MockMultipartFile("file", "contract.docx", "application/octet-stream", docxBytes("override")),
                PolicyOverrideRequest.of("invalid", null, null)
        ));
    }

    @Test
    void allowsSupportedExtensions() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertDoesNotThrow(() -> validationService.validateOrThrow(
                new MockMultipartFile(
                        "file",
                        "contract.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docxBytes("contract")
                )
        ));
    }

    @Test
    void rejectsSupportedExtensionWhenContentSignatureDoesNotMatch() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile(
                                "file",
                                "malicious.php.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "<?php echo 'owned'; ?>".getBytes(StandardCharsets.UTF_8)
                        )
                )
        );

        assertEquals("File content does not match file extension.", ex.getMessage());
    }

    @Test
    void rejectsUnsupportedDoubleExtension() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile(
                                "file",
                                "malicious.php.jpg",
                                "image/jpeg",
                                "<?php".getBytes(StandardCharsets.UTF_8)
                        )
                )
        );

        assertEquals("File extension is not supported.", ex.getMessage());
    }

    @Test
    void allowsPdfWhenContentSignatureMatches() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertDoesNotThrow(() -> validationService.validateOrThrow(
                new MockMultipartFile(
                        "file",
                        "report.pdf",
                        "application/pdf",
                        "%PDF-1.7\n".getBytes(StandardCharsets.UTF_8)
                )
        ));
    }

    @Test
    void rejectsMissingExtension() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("File extension is required.", ex.getMessage());
    }

    @Test
    void rejectsFilenameWithNullByte() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp\0.pdf", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("File name is invalid.", ex.getMessage());
    }

    @Test
    void rejectsBlankFilenameOrMissingName() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "", "application/octet-stream", new byte[] {1})
                )
        );
    }

    @Test
    void rejectsNullFilename() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", (String) null, "application/octet-stream", new byte[] {1})
                )
        );
    }

    @Test
    void rejectsOversizedPayload() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setMaxUploadSizeBytes(2L);
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1, 2, 3})
                )
        );
    }

    @Test
    void rejectsNullFile() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(null)
        );

        assertEquals("File is required.", ex.getMessage());
    }

    @Test
    void rejectsEmptyFile() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.docx", "application/octet-stream", new byte[0])
                )
        );

        assertEquals("File is required.", ex.getMessage());
    }

    @Test
    void rejectsMultipartWithNullOriginalFilename() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getSize()).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(file)
        );

        assertEquals("File extension is required.", ex.getMessage());
    }

    @Test
    void rejectsFilenameEndingWithDot() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("File extension is required.", ex.getMessage());
    }

    @Test
    void rejectsLeadingDotFilenameAsMissingExtension() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", ".hwp", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("File extension is required.", ex.getMessage());
    }

    @Test
    void trimsFilenameBeforeBlockedExtensionCheck() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "  contract.hwp  ", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("hwp", ex.getExtension());
    }

    @Test
    void handlesNullOverrideRequestByFallingBackToDefaultPolicy() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        null
                )
        );

        assertEquals("hwp", ex.getExtension());
    }

    @Test
    void sanitizeForLogReturnsEmptyWhenInputIsNull() throws Exception {
        ConversionProperties conversionProperties = new ConversionProperties();
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);
        Method method = DefaultDocumentValidationService.class.getDeclaredMethod("sanitizeForLog", String.class);
        method.setAccessible(true);

        String sanitized = (String) method.invoke(validationService, new Object[] {null});

        assertEquals("", sanitized);
    }

    @Test
    void sanitizeForLogReplacesTabCharacter() throws Exception {
        ConversionProperties conversionProperties = new ConversionProperties();
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);
        Method method = DefaultDocumentValidationService.class.getDeclaredMethod("sanitizeForLog", String.class);
        method.setAccessible(true);

        String sanitized = (String) method.invoke(validationService, "approver\tid");

        assertEquals("approver_id", sanitized);
    }

    @Test
    void throwsWhenSha256DigestIsUnavailableForOverrideAuditFingerprint() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        synchronized (SECURITY_PROVIDERS_LOCK) {
            Provider[] providers = Security.getProviders();
            for (Provider provider : providers) {
                Security.removeProvider(provider.getName());
            }

            try {
                IllegalStateException ex = assertThrows(
                        IllegalStateException.class,
                        () -> validationService.validateOrThrow(
                                new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                                PolicyOverrideRequest.of("true", "token-123", "approver-1")
                        )
                );

                assertEquals("SHA-256 digest unavailable", ex.getMessage());
            } finally {
                for (int index = 0; index < providers.length; index++) {
                    Security.insertProviderAt(providers[index], index + 1);
                }
            }
        }
    }

    private static byte[] docxBytes(String payload) {
        byte[] suffix = payload.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[suffix.length + 4];
        bytes[0] = 0x50;
        bytes[1] = 0x4B;
        bytes[2] = 0x03;
        bytes[3] = 0x04;
        System.arraycopy(suffix, 0, bytes, 4, suffix.length);
        return bytes;
    }
}

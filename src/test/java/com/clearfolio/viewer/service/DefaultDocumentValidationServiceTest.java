package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertDoesNotThrow(() -> validationService.validateOrThrow(
                new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                PolicyOverrideRequest.of("true", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1")
        ));
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideFlagIsInvalid() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of("not-boolean", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1")
                )
        );

        assertEquals("X-Clearfolio-Policy-Override must be true or false.", ex.getMessage());
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideTokenIsMissing() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of("true", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", " ")
                )
        );

        assertEquals("X-Clearfolio-Approver-Id is required when policy override is true.", ex.getMessage());
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideFlagIsFalse() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of("false", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1")
                )
        );

        assertEquals("hwp", ex.getExtension());
    }

    @Test
    void rejectsBlockedExtensionWhenOverrideFlagIsBlank() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                        PolicyOverrideRequest.of(" ", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1")
                )
        );

        assertEquals("hwp", ex.getExtension());
    }

    @Test
    void ignoresInvalidOverrideFlagForSupportedExtension() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertDoesNotThrow(() -> validationService.validateOrThrow(
                new MockMultipartFile("file", "contract.docx", "application/octet-stream", new byte[] {1}),
                PolicyOverrideRequest.of("invalid", null, null)
        ));
    }

    @Test
    void allowsSupportedExtensions() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertDoesNotThrow(() -> validationService.validateOrThrow(
                new MockMultipartFile("file", "contract.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1})
        ));
    }

    @Test
    void rejectsMissingExtension() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
    void rejectsBlankFilenameOrMissingName() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
    void rejectsInvalidSignature() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                                new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                                PolicyOverrideRequest.of("true", "invalid-token", "approver-1")
                )
        );

        assertEquals("Invalid policy override signature or public key.", ex.getMessage());
    }

    @Test
    void rejectsWhenPolicyOverrideSecretIsMissing() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("");
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                                new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1}),
                                PolicyOverrideRequest.of("true", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1")
                )
        );

        assertEquals("Policy override public key is not configured.", ex.getMessage());
    }

    @Test
    void throwsWhenSha256DigestIsUnavailableForOverrideAuditFingerprint() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setPolicyOverridePublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB");

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
                                PolicyOverrideRequest.of("true", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1")
                        )
                );

                assertEquals("RSA SHA-256 unavailable or key invalid", ex.getMessage());
            } finally {
                for (int index = 0; index < providers.length; index++) {
                    Security.insertProviderAt(providers[index], index + 1);
                }
            }
        }
    }
}

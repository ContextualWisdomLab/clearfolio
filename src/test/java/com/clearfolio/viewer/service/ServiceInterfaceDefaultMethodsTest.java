package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.model.ConversionJob;

class ServiceInterfaceDefaultMethodsTest {

    @Test
    void documentConversionServiceDefaultMethodDelegatesToLegacySubmit() {
        UUID expected = UUID.randomUUID();
        DocumentConversionService service = new DocumentConversionService() {
            @Override
            public UUID submit(MultipartFile file) {
                return expected;
            }

            @Override
            public Optional<ConversionJob> getJob(UUID jobId) {
                return Optional.empty();
            }

            @Override
            public RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId) {
                return RetryDeadLetterResult.NOT_FOUND;
            }
        };

        UUID actual = service.submit(
                new MockMultipartFile("file", "report.docx", "application/octet-stream", new byte[] {1}),
                PolicyOverrideRequest.of("true", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1")
        );

        assertEquals(expected, actual);
    }

    @Test
    void documentValidationServiceDefaultMethodDelegatesToLegacyValidation() {
        AtomicReference<MultipartFile> capturedFile = new AtomicReference<>();
        DocumentValidationService service = capturedFile::set;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                "application/octet-stream",
                new byte[] {1}
        );

        service.validateOrThrow(file, PolicyOverrideRequest.of("true", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1"));

        assertEquals(file, capturedFile.get());
    }
}

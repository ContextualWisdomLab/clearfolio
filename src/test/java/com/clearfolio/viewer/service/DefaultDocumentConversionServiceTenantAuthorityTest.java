package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Verifies that the production tenant-aware submission boundary never invents
 * demo authority when an authenticated tenant context is absent.
 */
class DefaultDocumentConversionServiceTenantAuthorityTest {

    @Test
    void tenantAwareSubmitRejectsMissingAuthorityBeforeAnyDocumentOrJobWork() {
        ConversionJobRepository repository = mock(ConversionJobRepository.class);
        DocumentValidationService validationService = mock(DocumentValidationService.class);
        ConversionWorker worker = mock(ConversionWorker.class);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        DefaultDocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                validationService,
                worker,
                artifactStore,
                new ConversionProperties()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "sensitive-document".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(file, PolicyOverrideRequest.none(), null)
        );

        assertEquals("tenant context is required", exception.getMessage());
        verifyNoInteractions(validationService, repository, worker, artifactStore);
    }
}

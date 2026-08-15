package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.api.ArtifactLinkRequest;
import com.clearfolio.viewer.api.ArtifactLinkResponse;
import com.clearfolio.viewer.artifact.ArtifactLinkService;
import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;

/**
 * Verifies that token preauthorization does not replace the post-lookup tenant
 * and artifact binding check performed immediately before bytes are returned.
 */
class ArtifactControllerPostLookupBindingTest {

    @Test
    void rejectsPreauthorizedTokenWhenResolvedJobBelongsToAnotherTenant() {
        UUID docId = UUID.randomUUID();
        byte[] pdfBytes = new byte[] {0, 1, 2, 3};
        ArtifactStore artifactStore = new InMemoryArtifactStore();
        artifactStore.putPdf(docId, pdfBytes);
        ArtifactLinkService artifactLinkService = new ArtifactLinkService(artifactStore, "test-secret");

        ConversionJob authorizedJob = succeededJob(docId, TenantContext.DEMO_TENANT_ID);
        ArtifactLinkResponse link = artifactLinkService.createLink(
                authorizedJob,
                tenantContext(),
                ArtifactLinkRequest.viewerPreview()
        );

        DocumentConversionService conversionService = mock(DocumentConversionService.class);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(succeededJob(docId, "other-tenant")));
        ArtifactController controller = new ArtifactController(
                conversionService,
                artifactStore,
                artifactLinkService,
                new TenantAccessService()
        );

        WebTestClient.bindToController(controller)
                .build()
                .get()
                .uri(link.artifactUrl())
                .exchange()
                .expectStatus().isForbidden();
    }

    private static TenantContext tenantContext() {
        return new TenantContext(
                TenantContext.DEMO_TENANT_ID,
                TenantContext.DEMO_SUBJECT_ID,
                Set.of(TenantPermissions.ARTIFACT_LINK_CREATE, TenantPermissions.VIEWER_READ)
        );
    }

    private static ConversionJob succeededJob(UUID docId, String tenantId) {
        ConversionJob job = new ConversionJob(
                docId,
                tenantId,
                TenantContext.DEMO_SUBJECT_ID,
                "report.docx",
                "application/octet-stream",
                "hash",
                4L,
                1
        );
        job.markSucceeded("/artifacts/" + docId + ".pdf", "done");
        return job;
    }
}

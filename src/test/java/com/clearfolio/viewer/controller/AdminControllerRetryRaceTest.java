package com.clearfolio.viewer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

/**
 * Regression coverage for the ownership-check-to-retry disappearance boundary.
 */
class AdminControllerRetryRaceTest {

    @Test
    void retryReturnsNotFoundWhenOwnedJobDisappearsAfterOwnershipCheck() {
        DocumentConversionService conversionService = mock(DocumentConversionService.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        TenantContext tenantContext = new TenantContext(
                "tenant", "subject", Set.of(TenantPermissions.ADMIN_WRITE));
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                tenantContext.tenantId(),
                tenantContext.subjectId(),
                "a.pdf",
                "application/pdf",
                "hash",
                100L,
                3);

        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(tenantContext);
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));
        when(conversionService.retryDeadLettered(jobId, "admin"))
                .thenReturn(RetryDeadLetterResult.NOT_FOUND);

        WebTestClient.bindToController(new AdminController(conversionService, tenantAccessService))
                .controllerAdvice(new ApiExceptionHandler())
                .build()
                .post()
                .uri("/api/v1/admin/convert/jobs/" + jobId + "/retry")
                .exchange()
                .expectStatus().isNotFound();
    }
}

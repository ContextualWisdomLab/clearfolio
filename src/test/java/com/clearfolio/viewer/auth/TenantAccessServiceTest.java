package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.model.ConversionJob;

class TenantAccessServiceTest {

    private final TenantAccessService service = new TenantAccessService();

    @Test
    void requireReturnsContextWhenPermissionIsPresent() {
        TenantContext context = service.require(headers(TenantPermissions.JOB_READ), TenantPermissions.JOB_READ);

        assertEquals(TenantContext.DEMO_TENANT_ID, context.tenantId());
    }

    @Test
    void requireRejectsMissingClaims() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.require(new HttpHeaders(), TenantPermissions.JOB_READ)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void requireRejectsMissingPermission() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.require(headers(TenantPermissions.JOB_READ), TenantPermissions.JOB_RETRY)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void requireSameTenantRejectsCrossTenantJobAsNotFound() {
        TenantContext context = new TenantContext("tenant-a", "user-1", Set.of(TenantPermissions.JOB_READ));
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-b",
                "user-2",
                "report.docx",
                "application/octet-stream",
                "hash",
                1L,
                1
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.requireSameTenant(context, job)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void requireSameTenantAcceptsOwnedJob() {
        TenantContext context = new TenantContext("tenant-a", "user-1", Set.of(TenantPermissions.JOB_READ));
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "user-1",
                "report.docx",
                "application/octet-stream",
                "hash",
                1L,
                1
        );

        assertDoesNotThrow(() -> service.requireSameTenant(context, job));
    }

    private static HttpHeaders headers(String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TenantContext.TENANT_ID_HEADER, TenantContext.DEMO_TENANT_ID);
        headers.add(TenantContext.SUBJECT_ID_HEADER, TenantContext.DEMO_SUBJECT_ID);
        headers.add(TenantContext.PERMISSIONS_HEADER, permissions);
        return headers;
    }
}

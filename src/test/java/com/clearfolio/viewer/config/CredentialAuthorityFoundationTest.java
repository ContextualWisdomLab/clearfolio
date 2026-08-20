package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;

import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.credential.CredentialRegistry;

class CredentialAuthorityFoundationTest {

    private static final String TENANT_SECRET = "t".repeat(32);
    private static final String ARTIFACT_SECRET = "a".repeat(32);
    private static final Instant NOW = Instant.parse("2026-08-20T14:30:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void mountedRegistryDrivesReadinessAndSignedTenantAuthorization() throws IOException {
        CredentialRegistryConfigTest.writeTenantCredential(
                tempDirectory,
                "v1",
                TENANT_SECRET
        );
        CredentialRegistry registry = new CredentialRegistryConfig()
                .credentialRegistry(tempDirectory.toString());

        assertThatCode(() -> new ProductionAuthReadinessConfig(registry, ARTIFACT_SECRET))
                .doesNotThrowAnyException();

        TenantAccessService tenantAccessService = new TenantAccessService(
                registry,
                300L,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        HttpHeaders headers = signedHeaders();

        TenantContext context = tenantAccessService.require(headers, TenantPermissions.JOB_READ);

        assertThat(context.tenantId()).isEqualTo("tenant-a");
        assertThat(context.subjectId()).isEqualTo("subject-a");
        assertThat(context.permissions()).containsExactly(TenantPermissions.JOB_READ);
    }

    private static HttpHeaders signedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantContext.TENANT_ID_HEADER, "tenant-a");
        headers.set(TenantContext.SUBJECT_ID_HEADER, "subject-a");
        headers.set(TenantContext.PERMISSIONS_HEADER, TenantPermissions.JOB_READ);
        String issuedAt = String.valueOf(NOW.getEpochSecond());
        TenantContext context = TenantContext.fromHeaders(headers).orElseThrow();
        headers.set(TenantContext.CLAIMS_ISSUED_AT_HEADER, issuedAt);
        headers.set(
                TenantContext.CLAIMS_SIGNATURE_HEADER,
                TenantAccessService.signClaims(context, issuedAt, TENANT_SECRET)
        );
        return headers;
    }
}

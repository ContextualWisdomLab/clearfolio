package com.clearfolio.viewer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Proves that the tenant-aware conversion-job constructor never manufactures
 * production authority when explicit tenant or subject claims are absent.
 */
class ConversionJobTenantAuthorityTest {

    @Test
    void explicitConstructorRejectsMissingTenantAuthority() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionJob(
                        UUID.randomUUID(),
                        null,
                        "subject-a",
                        "report.pdf",
                        "application/pdf",
                        "hash",
                        10L,
                        3
                )
        );

        assertEquals("tenantId must not be blank", exception.getMessage());
    }

    @Test
    void explicitConstructorRejectsBlankTenantAuthorityAfterSanitization() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionJob(
                        UUID.randomUUID(),
                        " \u0000 ",
                        "subject-a",
                        "report.pdf",
                        "application/pdf",
                        "hash",
                        10L,
                        3
                )
        );

        assertEquals("tenantId must not be blank", exception.getMessage());
    }

    @Test
    void explicitConstructorRejectsMissingSubjectAuthority() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionJob(
                        UUID.randomUUID(),
                        "tenant-a",
                        null,
                        "report.pdf",
                        "application/pdf",
                        "hash",
                        10L,
                        3
                )
        );

        assertEquals("subjectId must not be blank", exception.getMessage());
    }

    @Test
    void explicitConstructorRejectsBlankSubjectAuthorityAfterSanitization() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionJob(
                        UUID.randomUUID(),
                        "tenant-a",
                        " \u0000 ",
                        "report.pdf",
                        "application/pdf",
                        "hash",
                        10L,
                        3
                )
        );

        assertEquals("subjectId must not be blank", exception.getMessage());
    }
}

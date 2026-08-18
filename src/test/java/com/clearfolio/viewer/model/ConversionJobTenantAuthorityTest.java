package com.clearfolio.viewer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Proves that the tenant-aware conversion-job constructor never manufactures
 * or normalizes unsafe production authority when explicit claims are absent or
 * control-corrupted.
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

        assertEquals("tenantId must not contain control characters", exception.getMessage());
    }

    @Test
    void explicitConstructorRejectsControlCorruptedTenantAuthority() {
        for (String tenantId : List.of("tenant\u0000-a", "tenant\n-a", "tenant\u001B-a")) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ConversionJob(
                            UUID.randomUUID(),
                            tenantId,
                            "subject-a",
                            "report.pdf",
                            "application/pdf",
                            "hash",
                            10L,
                            3
                    )
            );

            assertEquals("tenantId must not contain control characters", exception.getMessage());
        }
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

        assertEquals("subjectId must not contain control characters", exception.getMessage());
    }

    @Test
    void explicitConstructorRejectsControlCorruptedSubjectAuthority() {
        for (String subjectId : List.of("subject\u0000-a", "subject\t-a", "subject\u007F-a")) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ConversionJob(
                            UUID.randomUUID(),
                            "tenant-a",
                            subjectId,
                            "report.pdf",
                            "application/pdf",
                            "hash",
                            10L,
                            3
                    )
            );

            assertEquals("subjectId must not contain control characters", exception.getMessage());
        }
    }
}

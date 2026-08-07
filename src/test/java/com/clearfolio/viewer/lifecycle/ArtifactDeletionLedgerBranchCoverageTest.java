package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * Exercises defensive replay branches whose invalid combinations cannot be
 * produced by the validated public receipt constructor.
 */
class ArtifactDeletionLedgerBranchCoverageTest {

    private static final Instant START = Instant.parse("2026-08-06T12:00:00Z");

    @Test
    void replayTransitionRejectsEveryShortCircuitMismatch() throws Exception {
        Method validator = privateMethod(
                "validateReplayTransition",
                ArtifactDeletionReceipt.class,
                ArtifactDeletionReceipt.class,
                boolean.class
        );

        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.DELETION_REQUESTED, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START.plusSeconds(1), 0, null)
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.DELETION_REQUESTED, START, 0, null),
                receipt(ArtifactDeletionState.METADATA_TOMBSTONED, START.plusSeconds(1), 1, START)
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.DELETION_REQUESTED, START, 0, null),
                receipt(ArtifactDeletionState.DELETION_REQUESTED, START.plusSeconds(1), 0, null)
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.METADATA_TOMBSTONED, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START.plusSeconds(1), 1, START)
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.METADATA_TOMBSTONED, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, START.plusSeconds(1), 0, null)
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, START.plusSeconds(1), 1, START)
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, START.plusSeconds(1), 2, START.plusSeconds(1))
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START.plusSeconds(1), 0, null)
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, START, 1, START),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, START.plusSeconds(1), 1, START)
        );
        assertInvalidTransition(
                validator,
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START.plusSeconds(1), 0, null)
        );
    }

    @Test
    void replayTransitionAcceptsChecksumCaptureAndMonotonicLifecycleOutcomes() throws Exception {
        Method validator = privateMethod(
                "validateReplayTransition",
                ArtifactDeletionReceipt.class,
                ArtifactDeletionReceipt.class,
                boolean.class
        );

        validator.invoke(
                null,
                receipt(ArtifactDeletionState.DELETION_REQUESTED, START, 0, null),
                receipt(ArtifactDeletionState.DELETION_REQUESTED, START.plusSeconds(1), 0, null),
                true
        );
        validator.invoke(
                null,
                receipt(ArtifactDeletionState.DELETION_REQUESTED, START, 0, null),
                receipt(ArtifactDeletionState.METADATA_TOMBSTONED, START.plusSeconds(1), 0, null),
                false
        );
        validator.invoke(
                null,
                receipt(ArtifactDeletionState.METADATA_TOMBSTONED, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START.plusSeconds(1), 0, null),
                false
        );
        validator.invoke(
                null,
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START, 0, null),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_COMPLETED, START.plusSeconds(1), 0, null),
                false
        );
        validator.invoke(
                null,
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START, 0, null),
                receipt(
                        ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED,
                        START.plusSeconds(1),
                        1,
                        START.plusSeconds(1)
                ),
                false
        );
        validator.invoke(
                null,
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_FAILED, START, 1, START),
                receipt(ArtifactDeletionState.ARTIFACT_CLEANUP_PENDING, START.plusSeconds(1), 1, START),
                false
        );
    }

    @Test
    void privateCodecHelpersCoverNullBlankAndPresentValues() throws Exception {
        Method decodeRequired = privateMethod("decodeRequired", String.class);
        Method decodeOptional = privateMethod("decodeOptional", String.class);
        Method parseOptionalInstant = privateMethod("optionalInstant", String.class);
        Method formatOptionalInstant = privateMethod("optionalInstant", Instant.class);
        Method pathOf = privateMethod("pathOf", String.class);
        String encodedTenant = encode("tenant-edge");
        String encodedBlank = encode("   ");

        assertEquals("tenant-edge", decodeRequired.invoke(null, encodedTenant));
        assertThrows(InvocationTargetException.class, () -> decodeRequired.invoke(null, "-"));
        assertThrows(InvocationTargetException.class, () -> decodeRequired.invoke(null, encodedBlank));
        assertNull(decodeOptional.invoke(null, "-"));
        assertEquals("tenant-edge", decodeOptional.invoke(null, encodedTenant));
        assertNull(parseOptionalInstant.invoke(null, "-"));
        assertEquals(START, parseOptionalInstant.invoke(null, START.toString()));
        assertEquals("-", formatOptionalInstant.invoke(null, new Object[] {null}));
        assertEquals(START.toString(), formatOptionalInstant.invoke(null, START));
        assertNull(pathOf.invoke(null, new Object[] {null}));
        assertNull(pathOf.invoke(null, "   "));
        assertEquals(Path.of("ledger.log"), pathOf.invoke(null, " ledger.log "));
    }

    private static Method privateMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ArtifactDeletionLedger.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static void assertInvalidTransition(
            Method validator,
            ArtifactDeletionReceipt current,
            ArtifactDeletionReceipt replayed
    ) {
        assertThrows(
                InvocationTargetException.class,
                () -> validator.invoke(null, current, replayed, false)
        );
    }

    private static ArtifactDeletionReceipt receipt(
            ArtifactDeletionState state,
            Instant stateChangedAt,
            int attempts,
            Instant lastAttemptAt
    ) {
        ArtifactDeletionReceipt receipt = mock(ArtifactDeletionReceipt.class);
        when(receipt.state()).thenReturn(state);
        when(receipt.stateChangedAt()).thenReturn(stateChangedAt);
        when(receipt.attemptCount()).thenReturn(attempts);
        when(receipt.lastAttemptAt()).thenReturn(lastAttemptAt);
        return receipt;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}

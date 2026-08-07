package com.clearfolio.viewer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Exercises defensive replay branches whose invalid combinations cannot be
 * produced by the validated public receipt constructor.
 */
class ArtifactDeletionLedgerBranchCoverageTest {

    private static final Instant START = Instant.parse("2026-08-06T12:00:00Z");
    private static final String CHECKSUM =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

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
    void checksumCaptureDetectorCoversEveryConjunct() throws Exception {
        Method detector = privateMethod(
                "isChecksumCaptureTransition",
                ArtifactDeletionReceipt.class,
                ArtifactDeletionReceipt.class
        );

        assertFalse(detectChecksumCapture(
                detector,
                checksumReceipt(ArtifactDeletionState.METADATA_TOMBSTONED, true, 0, null),
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, false, 0, null),
                true
        ));
        assertFalse(detectChecksumCapture(
                detector,
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, true, 0, null),
                checksumReceipt(ArtifactDeletionState.METADATA_TOMBSTONED, false, 0, null),
                true
        ));
        assertFalse(detectChecksumCapture(
                detector,
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, false, 0, null),
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, false, 0, null),
                true
        ));
        assertFalse(detectChecksumCapture(
                detector,
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, true, 0, null),
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, true, 0, null),
                true
        ));
        assertFalse(detectChecksumCapture(
                detector,
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, true, 0, null),
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, false, 0, null),
                false
        ));
        assertFalse(detectChecksumCapture(
                detector,
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, true, 0, null),
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, false, 1, START),
                true
        ));
        assertTrue(detectChecksumCapture(
                detector,
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, true, 0, null),
                checksumReceipt(ArtifactDeletionState.DELETION_REQUESTED, false, 0, null),
                true
        ));
    }

    @Test
    void recordChecksumRejectsMissingReceiptAndChangedRequestIdentity() throws Exception {
        ArtifactDeletionLedger ledger = new ArtifactDeletionLedger();
        UUID missingJobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        IllegalStateException missing = assertThrows(
                IllegalStateException.class,
                () -> ledger.recordArtifactChecksum(missingJobId, CHECKSUM, START)
        );
        assertEquals("artifact deletion receipt not found", missing.getMessage());

        UUID conflictingJobId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
        ArtifactDeletionReceipt current = mock(ArtifactDeletionReceipt.class);
        ArtifactDeletionReceipt changedIdentity = mock(ArtifactDeletionReceipt.class);
        when(current.captureArtifactChecksum(CHECKSUM, START)).thenReturn(changedIdentity);
        when(current.hasSameRequestIdentity(changedIdentity)).thenReturn(false);
        receiptMap(ledger).put(conflictingJobId, current);

        IllegalStateException conflict = assertThrows(
                IllegalStateException.class,
                () -> ledger.recordArtifactChecksum(conflictingJobId, CHECKSUM, START)
        );
        assertEquals("artifact deletion receipt conflicts with the active lifecycle", conflict.getMessage());
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

    private static boolean detectChecksumCapture(
            Method detector,
            ArtifactDeletionReceipt current,
            ArtifactDeletionReceipt replayed,
            boolean sameIdentity
    ) throws ReflectiveOperationException {
        when(current.hasSameRequestIdentity(replayed)).thenReturn(sameIdentity);
        return (boolean) detector.invoke(null, current, replayed);
    }

    private static ArtifactDeletionReceipt checksumReceipt(
            ArtifactDeletionState state,
            boolean pending,
            int attempts,
            Instant lastAttemptAt
    ) {
        ArtifactDeletionReceipt receipt = receipt(state, START, attempts, lastAttemptAt);
        when(receipt.isArtifactChecksumPending()).thenReturn(pending);
        return receipt;
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

    @SuppressWarnings("unchecked")
    private static Map<UUID, ArtifactDeletionReceipt> receiptMap(ArtifactDeletionLedger ledger)
            throws ReflectiveOperationException {
        Field field = ArtifactDeletionLedger.class.getDeclaredField("receiptsByJobId");
        field.setAccessible(true);
        return (Map<UUID, ArtifactDeletionReceipt>) field.get(ledger);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}

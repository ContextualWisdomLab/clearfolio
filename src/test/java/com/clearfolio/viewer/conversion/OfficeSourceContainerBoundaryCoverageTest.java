package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Exercises duplicated ZIP metadata and path-policy branches at the Office source boundary. */
class OfficeSourceContainerBoundaryCoverageTest {

    private static final int LOCAL_FIXED = 30;
    private static final int CENTRAL_FIXED = 46;
    private static final int EOCD_LENGTH = 22;

    @Test
    void centralDirectoryRejectsEveryUnsupportedOffsetAndZip64Authority() {
        byte[] compressedSentinel = oneEntryZip("content.xml");
        putUnsignedInt(compressedSentinel, centralOffset(compressedSentinel) + 20, 0xffff_ffffL);
        assertMalformed(compressedSentinel);

        byte[] uncompressedSentinel = oneEntryZip("content.xml");
        putUnsignedInt(uncompressedSentinel, centralOffset(uncompressedSentinel) + 24, 0xffff_ffffL);
        assertMalformed(uncompressedSentinel);

        byte[] diskSentinel = oneEntryZip("content.xml");
        putUnsignedShort(diskSentinel, centralOffset(diskSentinel) + 34, 0xffff);
        assertMalformed(diskSentinel);

        byte[] otherDisk = oneEntryZip("content.xml");
        putUnsignedShort(otherDisk, centralOffset(otherDisk) + 34, 1);
        assertMalformed(otherDisk);

        byte[] localOffsetSentinel = oneEntryZip("content.xml");
        putUnsignedInt(localOffsetSentinel, centralOffset(localOffsetSentinel) + 42, 0xffff_ffffL);
        assertMalformed(localOffsetSentinel);

        byte[] localInsideCentralDirectory = oneEntryZip("content.xml");
        putUnsignedInt(
                localInsideCentralDirectory,
                centralOffset(localInsideCentralDirectory) + 42,
                centralOffset(localInsideCentralDirectory)
        );
        assertMalformed(localInsideCentralDirectory);

        byte[] wrongReferencedSignature = oneEntryZip("content.xml");
        putUnsignedInt(wrongReferencedSignature, centralOffset(wrongReferencedSignature) + 42, 1L);
        assertMalformed(wrongReferencedSignature);
    }

    @Test
    void validEmptyDeflatedEntryExercisesNonStoredContainerPath() {
        byte[] source = oneEntryZip("content.xml");
        putUnsignedShort(source, 8, 8);
        putUnsignedShort(source, centralOffset(source) + 10, 8);

        assertDoesNotThrow(() -> OfficeSourceContainerPreflight.requireQualifiedContainer(request("docx", source)));
    }

    @Test
    void centralRecordCannotAdvertiseBytesBeyondItsDeclaredDirectory() {
        byte[] source = oneEntryZip("content.xml");
        putUnsignedShort(source, centralOffset(source) + 28, "content.xml".length() + 1);

        assertMalformed(source);
    }

    @Test
    void referencedLocalHeaderMustFitBeforeCentralDirectory() {
        byte[] source = oneEntryZip("content.xml");
        int offset = centralOffset(source) - 20;
        putUnsignedInt(source, offset, 0x04034b50L);
        putUnsignedInt(source, centralOffset(source) + 42, offset);

        assertMalformed(source);
    }

    @Test
    void localHeaderMetadataMustMatchNameLengthAndRemainBounded() {
        byte[] nameMismatch = oneEntryZip("content.xml");
        putUnsignedShort(nameMismatch, 26, "content.xml".length() - 1);
        assertMalformed(nameMismatch);

        byte[] metadataPastCentral = oneEntryZip("content.xml");
        putUnsignedShort(metadataPastCentral, 28, 1);
        assertMalformed(metadataPastCentral);
    }

    @Test
    void localAndCentralDescriptorFlagsMustAgree() {
        byte[] source = oneEntryZip("content.xml");
        putUnsignedShort(source, centralOffset(source) + 8, 0x0008);

        assertMalformed(source);
    }

    @Test
    void nonDescriptorCrcAndSizesRemainDuplicatedAuthorities() {
        byte[] crcMismatch = oneEntryZip("content.xml");
        putUnsignedInt(crcMismatch, centralOffset(crcMismatch) + 16, 1L);
        assertMalformed(crcMismatch);

        byte[] compressedMismatch = oneEntryZip("content.xml");
        putUnsignedShort(compressedMismatch, 8, 8);
        putUnsignedShort(compressedMismatch, centralOffset(compressedMismatch) + 10, 8);
        putUnsignedInt(compressedMismatch, centralOffset(compressedMismatch) + 20, 1L);
        assertMalformed(compressedMismatch);

        byte[] uncompressedMismatch = oneEntryZip("content.xml");
        putUnsignedShort(uncompressedMismatch, 8, 8);
        putUnsignedShort(uncompressedMismatch, centralOffset(uncompressedMismatch) + 10, 8);
        putUnsignedInt(uncompressedMismatch, centralOffset(uncompressedMismatch) + 24, 1L);
        assertMalformed(uncompressedMismatch);
    }

    @Test
    void localAndCentralEntryNamesMustBeByteEqual() {
        byte[] source = oneEntryZip("content.xml");
        source[LOCAL_FIXED] = (byte) 'x';

        assertMalformed(source);
    }

    @Test
    void emptyRawEntryNameIsNotAQualifiedRelativePath() {
        byte[] source = oneEntryZip("content.xml");
        putUnsignedShort(source, 26, 0);
        putUnsignedShort(source, centralOffset(source) + 28, 0);

        assertPolicyDenied(source);
    }

    @Test
    void pathPolicyRejectsSlashBackslashDriveNulAndParentVariants() {
        assertPolicyDenied(oneEntryZip("/absolute"));
        assertPolicyDenied(oneEntryZip("\\absolute"));
        assertPolicyDenied(oneEntryZip("C:drive"));
        assertPolicyDenied(oneEntryZip("z:drive"));
        assertPolicyDenied(oneEntryZip("a\\b"));
        assertPolicyDenied(oneEntryZip("a\u0000b"));
        assertPolicyDenied(oneEntryZip("a/../b"));
        assertPolicyDenied(oneEntryZip("a/.."));
    }

    @Test
    void odfMetaInfDirectoryExercisesShortFragmentAndNameComparisons() {
        byte[] source = oneEntryZip("META-INF/");
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeSourceContainerPreflight.requireQualifiedContainer(request("odt", source))
        );

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("source ODF META-INF entry is not allowed", failure.getMessage());
    }

    @Test
    void unsupportedFormatAndCompoundSignaturePathsRemainExplicit() {
        byte[] compound = new byte[] {
                (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
        };
        assertDoesNotThrow(() -> OfficeSourceContainerPreflight.requireQualifiedContainer(request("doc", compound)));

        OfficeConversionException unsupported = assertThrows(
                OfficeConversionException.class,
                () -> OfficeSourceContainerPreflight.requireQualifiedContainer(request("rtf", compound))
        );
        assertEquals(OfficeConversionFailureCode.UNSUPPORTED_FORMAT, unsupported.failureCode());
    }

    private static void assertMalformed(byte[] source) {
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeSourceContainerPreflight.requireQualifiedContainer(request("docx", source))
        );
        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
    }

    private static void assertPolicyDenied(byte[] source) {
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> OfficeSourceContainerPreflight.requireQualifiedContainer(request("docx", source))
        );
        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
    }

    private static OfficeConversionRequest request(String format, byte[] source) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("17980b56-305c-494c-94fe-0bbc7826ba65"),
                19L,
                format,
                "policy-v1",
                "trace-container-coverage",
                source,
                1_000_000L,
                10
        );
    }

    private static byte[] oneEntryZip(String entryName) {
        byte[] name = entryName.getBytes(StandardCharsets.ISO_8859_1);
        int centralOffset = LOCAL_FIXED + name.length;
        int centralRecordLength = CENTRAL_FIXED + name.length;
        int eocdOffset = centralOffset + centralRecordLength;
        byte[] bytes = new byte[eocdOffset + EOCD_LENGTH];

        putUnsignedInt(bytes, 0, 0x04034b50L);
        putUnsignedShort(bytes, 4, 20);
        putUnsignedShort(bytes, 26, name.length);
        System.arraycopy(name, 0, bytes, LOCAL_FIXED, name.length);

        putUnsignedInt(bytes, centralOffset, 0x02014b50L);
        putUnsignedShort(bytes, centralOffset + 28, name.length);
        putUnsignedInt(bytes, centralOffset + 42, 0L);
        System.arraycopy(name, 0, bytes, centralOffset + CENTRAL_FIXED, name.length);

        putUnsignedInt(bytes, eocdOffset, 0x06054b50L);
        putUnsignedShort(bytes, eocdOffset + 8, 1);
        putUnsignedShort(bytes, eocdOffset + 10, 1);
        putUnsignedInt(bytes, eocdOffset + 12, centralRecordLength);
        putUnsignedInt(bytes, eocdOffset + 16, centralOffset);
        return bytes;
    }

    private static int centralOffset(byte[] source) {
        return (int) unsignedInt(source, source.length - EOCD_LENGTH + 16);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(
                Byte.toUnsignedInt(bytes[offset])
                        | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                        | (Byte.toUnsignedInt(bytes[offset + 2]) << 16)
                        | (Byte.toUnsignedInt(bytes[offset + 3]) << 24)
        );
    }

    private static void putUnsignedShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void putUnsignedInt(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}

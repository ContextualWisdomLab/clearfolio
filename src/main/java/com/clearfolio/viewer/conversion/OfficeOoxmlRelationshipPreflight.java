package com.clearfolio.viewer.conversion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Rejects externally resolved OOXML package relationships before provider invocation.
 *
 * <p>The common container preflight runs first and establishes bounded standard ZIP
 * framing, safe entry names, allowed compression methods, and matching local/central
 * metadata. This second boundary therefore reads only relationship parts identified by
 * the already-validated central directory, caps every expanded relationship part at one
 * MiB, parses XML with DTD and external-entity support disabled, and rejects relationships
 * whose package contract delegates target resolution outside the source package.</p>
 */
final class OfficeOoxmlRelationshipPreflight {

    private static final Set<String> OOXML_FORMATS = Set.of("docx", "xlsx", "pptx");
    private static final Pattern RELATIONSHIP_PART = Pattern.compile("(?:^|.*/)_rels/[^/]*\\.rels");
    private static final String RELATIONSHIP_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final QName RELATIONSHIP_ELEMENT = new QName(RELATIONSHIP_NAMESPACE, "Relationship");
    private static final byte[] ZIP_END_OF_CENTRAL_DIRECTORY = new byte[] {
            0x50, 0x4b, 0x05, 0x06
    };
    private static final int ZIP_LOCAL_HEADER_FIXED_LENGTH = 30;
    private static final int ZIP_CENTRAL_HEADER_FIXED_LENGTH = 46;
    private static final int ZIP_EOCD_MINIMUM_LENGTH = 22;
    private static final int ZIP_MAXIMUM_COMMENT_LENGTH = 65_535;
    private static final int ZIP_STORED_METHOD = 0;
    private static final int MAX_RELATIONSHIP_BYTES = 1_048_576;

    private OfficeOoxmlRelationshipPreflight() {
    }

    /**
     * Rejects external OOXML relationships while leaving non-OOXML formats unchanged.
     *
     * @param request request that already passed the common Office container preflight
     * @throws OfficeConversionException when a relationship part is oversized, malformed,
     *         or declares an external target
     */
    static void requireNoExternalRelationships(OfficeConversionRequest request) {
        if (!OOXML_FORMATS.contains(request.sourceFormat())) {
            return;
        }

        byte[] sourceBytes = request.sourceBytes();
        int eocdOffset = findEocdOffset(sourceBytes);
        int entryCount = unsignedShort(sourceBytes, eocdOffset + 10);
        int cursor = (int) unsignedInt(sourceBytes, eocdOffset + 16);
        for (int index = 0; index < entryCount; index++) {
            int compressionMethod = unsignedShort(sourceBytes, cursor + 10);
            long compressedSize = unsignedInt(sourceBytes, cursor + 20);
            long uncompressedSize = unsignedInt(sourceBytes, cursor + 24);
            int fileNameLength = unsignedShort(sourceBytes, cursor + 28);
            int extraFieldLength = unsignedShort(sourceBytes, cursor + 30);
            int fileCommentLength = unsignedShort(sourceBytes, cursor + 32);
            int localHeaderOffset = (int) unsignedInt(sourceBytes, cursor + 42);
            int nameOffset = cursor + ZIP_CENTRAL_HEADER_FIXED_LENGTH;
            String entryName = new String(
                    sourceBytes,
                    nameOffset,
                    fileNameLength,
                    StandardCharsets.ISO_8859_1
            );
            if (RELATIONSHIP_PART.matcher(entryName).matches()) {
                byte[] relationshipBytes = extractRelationship(
                        sourceBytes,
                        localHeaderOffset,
                        compressionMethod,
                        compressedSize,
                        uncompressedSize
                );
                requireNoExternalRelationship(relationshipBytes);
            }
            cursor = nameOffset + fileNameLength + extraFieldLength + fileCommentLength;
        }
    }

    private static byte[] extractRelationship(
            byte[] sourceBytes,
            int localHeaderOffset,
            int compressionMethod,
            long compressedSizeLong,
            long uncompressedSizeLong
    ) {
        if (uncompressedSizeLong > MAX_RELATIONSHIP_BYTES) {
            throw relationshipTooLarge();
        }
        int compressedSize = Math.toIntExact(compressedSizeLong);
        int uncompressedSize = Math.toIntExact(uncompressedSizeLong);
        int localNameLength = unsignedShort(sourceBytes, localHeaderOffset + 26);
        int localExtraLength = unsignedShort(sourceBytes, localHeaderOffset + 28);
        int dataOffset = localHeaderOffset
                + ZIP_LOCAL_HEADER_FIXED_LENGTH
                + localNameLength
                + localExtraLength;
        if (compressionMethod == ZIP_STORED_METHOD) {
            return Arrays.copyOfRange(sourceBytes, dataOffset, dataOffset + compressedSize);
        }

        Inflater inflater = new Inflater(true);
        try (ByteArrayInputStream compressed = new ByteArrayInputStream(
                sourceBytes,
                dataOffset,
                compressedSize
        ); InflaterInputStream input = new InflaterInputStream(compressed, inflater)) {
            byte[] expanded = input.readNBytes(uncompressedSize + 1);
            if (expanded.length != uncompressedSize) {
                throw invalidRelationshipPart();
            }
            return expanded;
        } catch (IOException ex) {
            throw invalidRelationshipPart();
        } finally {
            inflater.end();
        }
    }

    private static void requireNoExternalRelationship(byte[] relationshipBytes) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        try (ByteArrayInputStream input = new ByteArrayInputStream(relationshipBytes)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT
                            && RELATIONSHIP_ELEMENT.equals(reader.getName())
                            && "External".equalsIgnoreCase(reader.getAttributeValue(null, "TargetMode"))) {
                        throw externalRelationship();
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | IOException ex) {
            throw invalidRelationshipPart();
        }
    }

    private static int findEocdOffset(byte[] sourceBytes) {
        int latest = sourceBytes.length - ZIP_EOCD_MINIMUM_LENGTH;
        int earliest = Math.max(0, latest - ZIP_MAXIMUM_COMMENT_LENGTH);
        for (int offset = latest; offset >= earliest; offset--) {
            if (matchesAt(sourceBytes, offset, ZIP_END_OF_CENTRAL_DIRECTORY)
                    && offset + ZIP_EOCD_MINIMUM_LENGTH + unsignedShort(sourceBytes, offset + 20)
                    == sourceBytes.length) {
                return offset;
            }
        }
        throw invalidRelationshipPart();
    }

    private static boolean matchesAt(byte[] sourceBytes, int offset, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (sourceBytes[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static int unsignedShort(byte[] sourceBytes, int offset) {
        return Byte.toUnsignedInt(sourceBytes[offset])
                | (Byte.toUnsignedInt(sourceBytes[offset + 1]) << 8);
    }

    private static long unsignedInt(byte[] sourceBytes, int offset) {
        return Integer.toUnsignedLong(
                Byte.toUnsignedInt(sourceBytes[offset])
                        | (Byte.toUnsignedInt(sourceBytes[offset + 1]) << 8)
                        | (Byte.toUnsignedInt(sourceBytes[offset + 2]) << 16)
                        | (Byte.toUnsignedInt(sourceBytes[offset + 3]) << 24)
        );
    }

    private static OfficeConversionException externalRelationship() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.POLICY_DENIED,
                "source Office package contains an external relationship"
        );
    }

    private static OfficeConversionException relationshipTooLarge() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.POLICY_DENIED,
                "source OOXML relationship part exceeds maximum bytes"
        );
    }

    private static OfficeConversionException invalidRelationshipPart() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source OOXML relationship part is invalid"
        );
    }
}

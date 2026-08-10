package com.clearfolio.viewer.conversion;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Performs bounded, non-networked semantic checks on the OpenDocument package manifest.
 *
 * <p>The common ZIP preflight runs first and proves the package framing, allowed compression
 * methods, entry-name safety, duplicated local/central metadata, required manifest presence,
 * and optional {@code mimetype} placement. This second boundary extracts only the manifest
 * payload, bounds its expanded size, parses it as non-validating namespace-aware XML with
 * DTD and external-entity support disabled, requires the OpenDocument 1.4 manifest version
 * and at least one manifest file entry, binds ordinary ZIP files to exactly one manifest entry,
 * and enforces the ODF root media-type contract. It intentionally does not attempt full Relax
 * NG manifest-schema validation.</p>
 */
final class OfficeOdfManifestPreflight {

    private static final Map<String, String> ODF_MIMETYPE_BY_FORMAT = Map.of(
            "odt", "application/vnd.oasis.opendocument.text",
            "ods", "application/vnd.oasis.opendocument.spreadsheet",
            "odp", "application/vnd.oasis.opendocument.presentation"
    );
    private static final String MANIFEST_NAMESPACE =
            "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";
    private static final String SUPPORTED_MANIFEST_VERSION = "1.4";
    private static final String MANIFEST_ENTRY_PATH = "META-INF/manifest.xml";
    private static final String MIMETYPE_ENTRY_PATH = "mimetype";
    private static final byte[] MANIFEST_ENTRY_NAME =
            MANIFEST_ENTRY_PATH.getBytes(StandardCharsets.UTF_8);
    private static final byte[] MIMETYPE_ENTRY_NAME =
            MIMETYPE_ENTRY_PATH.getBytes(StandardCharsets.UTF_8);
    private static final byte[] ZIP_CENTRAL_DIRECTORY_HEADER = new byte[] {
            0x50, 0x4b, 0x01, 0x02
    };
    private static final byte[] ZIP_END_OF_CENTRAL_DIRECTORY = new byte[] {
            0x50, 0x4b, 0x05, 0x06
    };
    private static final int ZIP_LOCAL_HEADER_FIXED_LENGTH = 30;
    private static final int ZIP_CENTRAL_HEADER_FIXED_LENGTH = 46;
    private static final int ZIP_EOCD_MINIMUM_LENGTH = 22;
    private static final int ZIP_MAXIMUM_COMMENT_LENGTH = 65_535;
    private static final int ZIP_STORED_METHOD = 0;
    private static final int ZIP_DEFLATED_METHOD = 8;
    private static final int MAX_MANIFEST_BYTES = 1_048_576;

    private OfficeOdfManifestPreflight() {
    }

    /**
     * Validates the ODF manifest when the request is an ODF package candidate.
     *
     * @param request immutable conversion request that already passed common container preflight
     * @throws OfficeConversionException when the manifest cannot be safely extracted or parsed,
     *         does not advertise the supported OpenDocument manifest version or any file entry,
     *         ordinary package-file inventory is inconsistent, or the root-document media type
     *         disagrees with the package {@code mimetype}
     */
    static void requireQualifiedManifest(OfficeConversionRequest request) {
        String expectedMediaType = ODF_MIMETYPE_BY_FORMAT.get(request.sourceFormat());
        if (expectedMediaType == null) {
            return;
        }

        LocatedEntries entries = locateEntries(request.sourceBytes());
        byte[] manifestBytes = extractManifest(request.sourceBytes(), entries.manifestEntry());
        requireManifestContract(
                manifestBytes,
                entries.mimetypeFound(),
                expectedMediaType,
                entries.ordinaryPackageFiles()
        );
    }

    private static LocatedEntries locateEntries(byte[] sourceBytes) {
        int eocdOffset = findEocdOffset(sourceBytes);
        if (eocdOffset < 0) {
            throw invalidManifest();
        }
        int entryCount = unsignedShort(sourceBytes, eocdOffset + 10);
        long centralOffsetLong = unsignedInt(sourceBytes, eocdOffset + 16);
        if (centralOffsetLong > Integer.MAX_VALUE) {
            throw invalidManifest();
        }
        int cursor = (int) centralOffsetLong;
        ManifestEntry manifestEntry = null;
        boolean mimetypeFound = false;
        Set<String> ordinaryPackageFiles = new HashSet<>();

        for (int index = 0; index < entryCount; index++) {
            if (!matchesAt(sourceBytes, cursor, ZIP_CENTRAL_DIRECTORY_HEADER)
                    || cursor > sourceBytes.length - ZIP_CENTRAL_HEADER_FIXED_LENGTH) {
                throw invalidManifest();
            }
            int compressionMethod = unsignedShort(sourceBytes, cursor + 10);
            long compressedSize = unsignedInt(sourceBytes, cursor + 20);
            long uncompressedSize = unsignedInt(sourceBytes, cursor + 24);
            int fileNameLength = unsignedShort(sourceBytes, cursor + 28);
            int extraFieldLength = unsignedShort(sourceBytes, cursor + 30);
            int fileCommentLength = unsignedShort(sourceBytes, cursor + 32);
            long localHeaderOffset = unsignedInt(sourceBytes, cursor + 42);
            int nameOffset = cursor + ZIP_CENTRAL_HEADER_FIXED_LENGTH;
            long nextCursor = (long) nameOffset + fileNameLength + extraFieldLength + fileCommentLength;
            if (nextCursor > sourceBytes.length || localHeaderOffset > Integer.MAX_VALUE) {
                throw invalidManifest();
            }

            String entryName = new String(sourceBytes, nameOffset, fileNameLength, StandardCharsets.UTF_8);
            if (entryNameMatches(sourceBytes, nameOffset, fileNameLength, MIMETYPE_ENTRY_NAME)) {
                mimetypeFound = true;
            } else if (entryNameMatches(sourceBytes, nameOffset, fileNameLength, MANIFEST_ENTRY_NAME)) {
                int localOffset = (int) localHeaderOffset;
                if (localOffset > sourceBytes.length - ZIP_LOCAL_HEADER_FIXED_LENGTH) {
                    throw invalidManifest();
                }
                int localNameLength = unsignedShort(sourceBytes, localOffset + 26);
                int localExtraLength = unsignedShort(sourceBytes, localOffset + 28);
                long dataOffset = (long) localOffset
                        + ZIP_LOCAL_HEADER_FIXED_LENGTH
                        + localNameLength
                        + localExtraLength;
                if (dataOffset > Integer.MAX_VALUE) {
                    throw invalidManifest();
                }
                manifestEntry = new ManifestEntry(
                        compressionMethod,
                        compressedSize,
                        uncompressedSize,
                        (int) dataOffset
                );
            } else if (!entryName.startsWith("META-INF/") && !entryName.endsWith("/")) {
                ordinaryPackageFiles.add(entryName);
            }
            cursor = (int) nextCursor;
        }
        if (manifestEntry == null) {
            throw invalidManifest();
        }
        return new LocatedEntries(manifestEntry, mimetypeFound, Set.copyOf(ordinaryPackageFiles));
    }

    private static byte[] extractManifest(byte[] sourceBytes, ManifestEntry entry) {
        if (entry.uncompressedSize() > MAX_MANIFEST_BYTES) {
            throw manifestTooLarge();
        }
        if (entry.compressedSize() > Integer.MAX_VALUE || entry.uncompressedSize() > Integer.MAX_VALUE) {
            throw invalidManifest();
        }
        int compressedSize = (int) entry.compressedSize();
        int uncompressedSize = (int) entry.uncompressedSize();
        long dataEnd = (long) entry.dataOffset() + compressedSize;
        if (dataEnd > sourceBytes.length) {
            throw invalidManifest();
        }
        if (entry.compressionMethod() == ZIP_STORED_METHOD) {
            if (compressedSize != uncompressedSize) {
                throw invalidManifest();
            }
            return Arrays.copyOfRange(sourceBytes, entry.dataOffset(), (int) dataEnd);
        }
        if (entry.compressionMethod() != ZIP_DEFLATED_METHOD) {
            throw invalidManifest();
        }

        byte[] result = new byte[uncompressedSize];
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(sourceBytes, entry.dataOffset(), compressedSize);
            int written = 0;
            while (!inflater.finished() && written < result.length) {
                int produced = inflater.inflate(result, written, result.length - written);
                if (produced == 0) {
                    break;
                }
                written += produced;
            }
            if (!inflater.finished() || written != result.length || inflater.getRemaining() != 0) {
                throw invalidManifest();
            }
            return result;
        } catch (DataFormatException ex) {
            throw invalidManifest();
        } finally {
            inflater.end();
        }
    }

    private static void requireManifestContract(
            byte[] manifestBytes,
            boolean mimetypeFound,
            String expectedMediaType,
            Set<String> ordinaryPackageFiles
    ) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("external XML resolution is disabled");
        });

        boolean rootElementSeen = false;
        int manifestFileEntryCount = 0;
        String rootDocumentMediaType = null;
        Map<String, Integer> ordinaryManifestEntryCounts = new HashMap<>();
        try (ByteArrayInputStream input = new ByteArrayInputStream(manifestBytes)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                        throw invalidManifest();
                    }
                    if (event != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    if (!rootElementSeen) {
                        rootElementSeen = true;
                        if (!MANIFEST_NAMESPACE.equals(reader.getNamespaceURI())
                                || !"manifest".equals(reader.getLocalName())) {
                            throw invalidManifest();
                        }
                        String manifestVersion = reader.getAttributeValue(MANIFEST_NAMESPACE, "version");
                        if (!SUPPORTED_MANIFEST_VERSION.equals(manifestVersion)) {
                            throw unsupportedManifestVersion();
                        }
                    }
                    if (!MANIFEST_NAMESPACE.equals(reader.getNamespaceURI())
                            || !"file-entry".equals(reader.getLocalName())) {
                        continue;
                    }
                    manifestFileEntryCount++;

                    String fullPath = reader.getAttributeValue(MANIFEST_NAMESPACE, "full-path");
                    if (fullPath == null || fullPath.isEmpty()) {
                        throw invalidManifest();
                    }
                    if ("/".equals(fullPath)) {
                        if (rootDocumentMediaType != null) {
                            throw invalidManifest();
                        }
                        rootDocumentMediaType = reader.getAttributeValue(MANIFEST_NAMESPACE, "media-type");
                        continue;
                    }
                    if (MANIFEST_ENTRY_PATH.equals(fullPath) || MIMETYPE_ENTRY_PATH.equals(fullPath)) {
                        throw manifestInventoryMismatch();
                    }
                    if (!fullPath.startsWith("META-INF/") && !fullPath.endsWith("/")) {
                        ordinaryManifestEntryCounts.merge(fullPath, 1, Integer::sum);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | java.io.IOException ex) {
            throw invalidManifest();
        }

        if (!rootElementSeen) {
            throw invalidManifest();
        }
        if (manifestFileEntryCount == 0) {
            throw missingManifestFileEntry();
        }
        if (mimetypeFound && rootDocumentMediaType == null) {
            throw missingManifestRootEntry();
        }
        if (rootDocumentMediaType != null && !mimetypeFound) {
            throw missingMimetypeForManifestRoot();
        }
        if (rootDocumentMediaType != null && !expectedMediaType.equals(rootDocumentMediaType)) {
            throw manifestMediaTypeMismatch();
        }
        if (!ordinaryManifestEntryCounts.keySet().equals(ordinaryPackageFiles)
                || ordinaryManifestEntryCounts.values().stream().anyMatch(count -> count != 1)) {
            throw manifestInventoryMismatch();
        }
    }

    private static int findEocdOffset(byte[] sourceBytes) {
        if (sourceBytes.length < ZIP_EOCD_MINIMUM_LENGTH) {
            return -1;
        }
        int latest = sourceBytes.length - ZIP_EOCD_MINIMUM_LENGTH;
        int earliest = Math.max(0, latest - ZIP_MAXIMUM_COMMENT_LENGTH);
        for (int offset = latest; offset >= earliest; offset--) {
            if (matchesAt(sourceBytes, offset, ZIP_END_OF_CENTRAL_DIRECTORY)
                    && offset + ZIP_EOCD_MINIMUM_LENGTH + unsignedShort(sourceBytes, offset + 20)
                    == sourceBytes.length) {
                return offset;
            }
        }
        return -1;
    }

    private static boolean entryNameMatches(
            byte[] sourceBytes,
            int nameOffset,
            int nameLength,
            byte[] expectedName
    ) {
        return nameLength == expectedName.length && matchesAt(sourceBytes, nameOffset, expectedName);
    }

    private static boolean matchesAt(byte[] sourceBytes, int offset, byte[] expected) {
        if (offset < 0 || offset > sourceBytes.length - expected.length) {
            return false;
        }
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

    private static OfficeConversionException invalidManifest() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source ODF manifest is invalid"
        );
    }

    private static OfficeConversionException unsupportedManifestVersion() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source ODF manifest version is not allowed"
        );
    }

    private static OfficeConversionException missingManifestFileEntry() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source ODF manifest has no file entries"
        );
    }

    private static OfficeConversionException manifestTooLarge() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.POLICY_DENIED,
                "source ODF manifest exceeds maximum bytes"
        );
    }

    private static OfficeConversionException missingManifestRootEntry() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source ODF manifest root entry is missing"
        );
    }

    private static OfficeConversionException missingMimetypeForManifestRoot() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source ODF mimetype entry is missing for manifest root"
        );
    }

    private static OfficeConversionException manifestMediaTypeMismatch() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source ODF manifest root media type does not match mimetype"
        );
    }

    private static OfficeConversionException manifestInventoryMismatch() {
        return new OfficeConversionException(
                OfficeConversionFailureCode.MALFORMED_INPUT,
                "source ODF manifest does not match package file inventory"
        );
    }

    private record ManifestEntry(
            int compressionMethod,
            long compressedSize,
            long uncompressedSize,
            int dataOffset
    ) {
    }

    private record LocatedEntries(
            ManifestEntry manifestEntry,
            boolean mimetypeFound,
            Set<String> ordinaryPackageFiles
    ) {
    }
}

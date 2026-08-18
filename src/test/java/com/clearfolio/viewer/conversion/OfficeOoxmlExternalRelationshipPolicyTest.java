package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * External-relationship regressions for OOXML source packages.
 */
class OfficeOoxmlExternalRelationshipPolicyTest {

    private static final String RELATIONSHIP_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/relationships";

    @Test
    void adapterRejectsExternalRelationshipBeforeProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = docxWithRelationship(
                "<Relationship Id=\"rId1\" "
                        + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" "
                        + "Target=\"https://example.test/resource\" TargetMode=\"External\"/>"
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("source Office package contains an external relationship", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsRootExternalRelationshipBeforeProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = docxWithRelationshipAtPath(
                "_rels/.rels",
                "<Relationship Id=\"rId1\" "
                        + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                        + "Target=\"https://example.test/document.xml\" TargetMode=\"External\"/>"
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("source Office package contains an external relationship", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterAllowsInternalRelationshipToReachProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = docxWithRelationship(
                "<Relationship Id=\"rId1\" "
                        + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                        + "Target=\"media/image1.png\"/>"
        );

        countingAdapter(providerCalls).convert(request(source));

        assertEquals(1, providerCalls.get());
    }

    @Test
    void adapterAllowsStoredInternalRelationshipToReachProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = docxWithStoredRelationship(
                "<Relationship Id=\"rId1\" "
                        + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                        + "Target=\"media/image1.png\"/>"
        );

        countingAdapter(providerCalls).convert(request(source));

        assertEquals(1, providerCalls.get());
    }

    @Test
    void adapterRejectsMalformedRelationshipPartBeforeProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        byte[] source = docxWithRelationshipXml(
                "<Relationships xmlns=\"" + RELATIONSHIP_NAMESPACE + "\"><Relationship"
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source OOXML relationship part is invalid", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterRejectsOversizedRelationshipPartBeforeProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        String oversizedXml = "<Relationships xmlns=\"" + RELATIONSHIP_NAMESPACE + "\">"
                + " ".repeat(1_048_577)
                + "</Relationships>";
        byte[] source = docxWithRelationshipXml(oversizedXml);

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(source))
        );

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("source OOXML relationship part exceeds maximum bytes", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    private static OfficeConversionAdapter countingAdapter(AtomicInteger providerCalls) {
        return input -> {
            providerCalls.incrementAndGet();
            return new OfficeConversionResult(
                    "deterministic-fixture",
                    "1",
                    input.sourceSha256(),
                    input.binding(),
                    OfficeConversionTestPdf.onePage()
            );
        };
    }

    private static OfficeConversionRequest request(byte[] sourceBytes) {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("8d0fc0f7-971f-4fde-9188-b71a8bfbbdb4"),
                17L,
                "docx",
                "policy-v1",
                "trace-external-relationship-policy",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] docxWithRelationship(String relationshipElement) throws IOException {
        return docxWithRelationshipAtPath("word/_rels/document.xml.rels", relationshipElement);
    }

    private static byte[] docxWithRelationshipAtPath(
            String path,
            String relationshipElement
    ) throws IOException {
        return docxWithRelationshipXmlAtPath(
                path,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"" + RELATIONSHIP_NAMESPACE + "\">"
                        + relationshipElement
                        + "</Relationships>"
        );
    }

    private static byte[] docxWithRelationshipXml(String xml) throws IOException {
        return docxWithRelationshipXmlAtPath("word/_rels/document.xml.rels", xml);
    }

    private static byte[] docxWithRelationshipXmlAtPath(String path, String xml) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static byte[] docxWithStoredRelationship(String relationshipElement) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"" + RELATIONSHIP_NAMESPACE + "\">"
                + relationshipElement
                + "</Relationships>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        ZipEntry entry = new ZipEntry("word/_rels/document.xml.rels");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc32.getValue());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(entry);
            zip.write(bytes);
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}

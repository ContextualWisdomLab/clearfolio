package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"" + RELATIONSHIP_NAMESPACE + "\">"
                + relationshipElement
                + "</Relationships>";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}

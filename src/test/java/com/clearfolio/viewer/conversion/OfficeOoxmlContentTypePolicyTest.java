package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 * Active-content regressions for OOXML package content-type declarations.
 */
class OfficeOoxmlContentTypePolicyTest {

    private static final String CONTENT_TYPE_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String VBA_PROJECT_CONTENT_TYPE =
            "application/vnd.ms-office.vbaProject";

    @Test
    void adapterRejectsVbaContentTypeEvenWhenPartIsRenamed() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(docxWithContentTypes(
                        "<Override PartName=\"/word/customPayload.bin\" ContentType=\""
                                + VBA_PROJECT_CONTENT_TYPE
                                + "\"/>"
                )))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source Office package contains prohibited active content", failure.getMessage());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void adapterAllowsBenignContentTypeDeclarations() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();

        assertDoesNotThrow(() -> countingAdapter(providerCalls).convert(request(docxWithContentTypes(
                "<Override PartName=\"/word/document.xml\" "
                        + "ContentType=\"application/vnd.openxmlformats-officedocument."
                        + "wordprocessingml.document.main+xml\"/>"
        ))));

        assertEquals(1, providerCalls.get());
    }

    @Test
    void adapterRejectsMalformedContentTypesPartBeforeProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> countingAdapter(providerCalls).convert(request(docxWithRawContentTypes(
                        ("<Types xmlns=\"" + CONTENT_TYPE_NAMESPACE + "\"><Override").getBytes(
                                StandardCharsets.UTF_8
                        )
                )))
        );

        assertEquals(OfficeConversionFailureCode.MALFORMED_INPUT, failure.failureCode());
        assertEquals("source OOXML content types part is invalid", failure.getMessage());
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
                UUID.fromString("aa0ee237-7dbb-4765-9dfb-fad4ad5759a0"),
                19L,
                "docx",
                "policy-v1",
                "trace-ooxml-content-type-policy",
                sourceBytes,
                1_000_000L,
                10
        );
    }

    private static byte[] docxWithContentTypes(String declarations) throws IOException {
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"" + CONTENT_TYPE_NAMESPACE + "\">"
                + declarations
                + "</Types>";
        return docxWithRawContentTypes(contentTypes.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] docxWithRawContentTypes(byte[] contentTypes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(contentTypes);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/customPayload.bin"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}

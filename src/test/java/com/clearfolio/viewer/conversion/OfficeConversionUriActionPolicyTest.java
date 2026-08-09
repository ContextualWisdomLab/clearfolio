package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

/**
 * URI-scheme regressions for user-activated PDF link preservation.
 *
 * <p>Ordinary web and mail hyperlinks are inert navigation metadata at the
 * conversion boundary. Executable, local-file, embedded-data, malformed, and
 * custom-protocol URI actions fail closed because a PDF viewer may dispatch
 * those schemes to behavior outside ordinary hyperlink navigation.</p>
 */
class OfficeConversionUriActionPolicyTest {

    @Test
    void adapterPreservesHttpsAnnotationUri() throws IOException {
        assertDoesNotThrow(() -> convert(pdfWithUri("https://example.invalid/report")));
    }

    @Test
    void adapterPreservesHttpAnnotationUri() throws IOException {
        assertDoesNotThrow(() -> convert(pdfWithUri("http://example.invalid/report")));
    }

    @Test
    void adapterPreservesMailtoAnnotationUri() throws IOException {
        assertDoesNotThrow(() -> convert(pdfWithUri("mailto:security@example.invalid")));
    }

    @Test
    void adapterRejectsJavaScriptUriScheme() throws IOException {
        assertPolicyDenied(pdfWithUri("javascript:alert(1)"));
    }

    @Test
    void adapterRejectsLocalFileUriScheme() throws IOException {
        assertPolicyDenied(pdfWithUri("file:///etc/passwd"));
    }

    @Test
    void adapterRejectsEmbeddedDataUriScheme() throws IOException {
        assertPolicyDenied(pdfWithUri("data:text/html,%3Cscript%3Ealert(1)%3C/script%3E"));
    }

    @Test
    void adapterRejectsUnknownCustomUriScheme() throws IOException {
        assertPolicyDenied(pdfWithUri("clearfolio-custom:payload"));
    }

    @Test
    void adapterRejectsRelativeUriAction() throws IOException {
        assertPolicyDenied(pdfWithUri("relative/path"));
    }

    @Test
    void adapterRejectsMalformedUriAction() throws IOException {
        assertPolicyDenied(pdfWithUri("https://example.invalid/has space"));
    }

    private static void convert(byte[] pdf) {
        adapterReturning(pdf).convert(request());
    }

    private static void assertPolicyDenied(byte[] pdf) {
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> convert(pdf)
        );
        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    private static OfficeConversionAdapter adapterReturning(byte[] pdf) {
        return input -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                pdf
        );
    }

    private static OfficeConversionRequest request() {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("945bf4f3-48b6-475b-a253-c316969818e6"),
                9L,
                "docx",
                "policy-v1",
                "trace-uri-action-policy",
                OfficeConversionTestSource.zipPackage("fixture-source"),
                1_000_000L,
                10
        );
    }

    private static byte[] pdfWithUri(String uri) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            COSDictionary action = new COSDictionary();
            action.setItem(COSName.getPDFName("S"), COSName.getPDFName("URI"));
            action.setString(COSName.getPDFName("URI"), uri);

            COSDictionary annotation = new COSDictionary();
            annotation.setItem(COSName.TYPE, COSName.getPDFName("Annot"));
            annotation.setItem(COSName.SUBTYPE, COSName.getPDFName("Link"));
            annotation.setItem(COSName.getPDFName("A"), action);

            COSArray annotations = new COSArray();
            annotations.add(annotation);
            page.getCOSObject().setItem(COSName.getPDFName("Annots"), annotations);

            document.save(output);
            return output.toByteArray();
        }
    }
}

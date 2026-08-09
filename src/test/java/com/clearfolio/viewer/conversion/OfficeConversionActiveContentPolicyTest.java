package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

/**
 * Active-content policy regressions for converter-produced PDF candidates.
 */
class OfficeConversionActiveContentPolicyTest {

    @Test
    void adapterRejectsJavaScriptDocumentOpenAction() throws IOException {
        OfficeConversionRequest request = request();
        byte[] pdf = pdfWithJavaScriptOpenAction();
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    private static OfficeConversionRequest request() {
        return new OfficeConversionRequest(
                "tenant-a",
                UUID.fromString("945bf4f3-48b6-475b-a253-c316969818e6"),
                9L,
                "docx",
                "policy-v1",
                "trace-active-content",
                "fixture-source".getBytes(StandardCharsets.UTF_8),
                1_000_000L,
                10
        );
    }

    private static byte[] pdfWithJavaScriptOpenAction() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            COSDictionary javascriptAction = new COSDictionary();
            javascriptAction.setItem(COSName.getPDFName("S"), COSName.getPDFName("JavaScript"));
            javascriptAction.setString(COSName.getPDFName("JS"), "app.alert('clearfolio')");
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("OpenAction"), javascriptAction);
            document.save(output);
            return output.toByteArray();
        }
    }
}

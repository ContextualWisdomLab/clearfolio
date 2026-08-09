package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

/**
 * Active-content policy regressions for converter-produced PDF candidates.
 */
class OfficeConversionActiveContentPolicyTest {

    @Test
    void adapterRejectsJavaScriptDocumentOpenAction() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithJavaScriptOpenAction());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsDocumentJavaScriptNameTreeWithoutOpenAction() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithJavaScriptNameTree());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    private static OfficeConversionException assertPolicyDenied(byte[] pdf) {
        OfficeConversionRequest request = request();
        OfficeConversionAdapter adapter = input -> new OfficeConversionResult(
                "deterministic-fixture",
                "1",
                input.sourceSha256(),
                input.binding(),
                pdf
        );

        return assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request)
        );
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
            COSDictionary javascriptAction = javascriptAction();
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("OpenAction"), javascriptAction);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithJavaScriptNameTree() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());

            COSArray entries = new COSArray();
            entries.add(new COSString("clearfolio-startup"));
            entries.add(javascriptAction());

            COSDictionary javaScriptTree = new COSDictionary();
            javaScriptTree.setItem(COSName.getPDFName("Names"), entries);

            COSDictionary names = new COSDictionary();
            names.setItem(COSName.getPDFName("JavaScript"), javaScriptTree);
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("Names"), names);

            document.save(output);
            return output.toByteArray();
        }
    }

    private static COSDictionary javascriptAction() {
        COSDictionary javascriptAction = new COSDictionary();
        javascriptAction.setItem(COSName.getPDFName("S"), COSName.getPDFName("JavaScript"));
        javascriptAction.setString(COSName.getPDFName("JS"), "app.alert('clearfolio')");
        return javascriptAction;
    }
}

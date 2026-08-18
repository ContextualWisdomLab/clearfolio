package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

/** Covers benign and malformed PDF action shapes at the publication boundary. */
class OfficeConversionPdfBoundaryCoverageTest {

    @Test
    void adapterPreservesEveryDirectInternalOpenDestinationShape() throws IOException {
        COSArray arrayDestination = new COSArray();
        arrayDestination.add(COSName.getPDFName("section-one"));

        assertDoesNotThrow(() -> convert(pdfWithOpenAction(arrayDestination)));
        assertDoesNotThrow(() -> convert(pdfWithOpenAction(COSName.getPDFName("section-one"))));
        assertDoesNotThrow(() -> convert(pdfWithOpenAction(new COSString("section-one"))));
    }

    @Test
    void adapterPreservesAnnotationWithoutActionAndEmptyAdditionalActions() throws IOException {
        COSDictionary annotation = linkAnnotation();
        annotation.setItem(COSName.getPDFName("AA"), new COSDictionary());

        assertDoesNotThrow(() -> convert(pdfWithAnnotation(annotation)));
    }

    @Test
    void adapterRejectsMalformedAdditionalActionsContainer() throws IOException {
        COSDictionary annotation = linkAnnotation();
        annotation.setItem(COSName.getPDFName("AA"), COSName.getPDFName("malformed"));

        assertPolicyDenied(pdfWithAnnotation(annotation));
    }

    @Test
    void adapterRejectsDocumentUriActionEvenWhenSchemeWouldBeAllowedOnUserLink() throws IOException {
        COSDictionary uriAction = new COSDictionary();
        uriAction.setItem(COSName.getPDFName("S"), COSName.getPDFName("URI"));
        uriAction.setString(COSName.getPDFName("URI"), "https://example.invalid/report");

        assertPolicyDenied(pdfWithOpenAction(uriAction));
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
                "trace-pdf-boundary-coverage",
                OfficeConversionTestSource.zipPackage("fixture-source"),
                1_000_000L,
                10
        );
    }

    private static byte[] pdfWithOpenAction(COSBase openAction) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("OpenAction"), openAction);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithAnnotation(COSDictionary annotation) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            COSArray annotations = new COSArray();
            annotations.add(annotation);
            document.getPage(0).getCOSObject().setItem(COSName.getPDFName("Annots"), annotations);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static PDDocument onePageDocument() {
        PDDocument document = new PDDocument();
        document.addPage(new PDPage());
        return document;
    }

    private static COSDictionary linkAnnotation() {
        COSDictionary annotation = new COSDictionary();
        annotation.setItem(COSName.TYPE, COSName.getPDFName("Annot"));
        annotation.setItem(COSName.SUBTYPE, COSName.getPDFName("Link"));
        return annotation;
    }
}

package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

/**
 * Edge-case regressions for the fail-closed PDF action publication boundary.
 */
class OfficeConversionActionBoundaryTest {

    @Test
    void preservesNamedDocumentDestination() throws IOException {
        assertDoesNotThrow(() -> convert(pdfWithOpenAction(COSName.getPDFName("section-one"))));
    }

    @Test
    void preservesStringDocumentDestination() throws IOException {
        assertDoesNotThrow(() -> convert(pdfWithOpenAction(new COSString("section-one"))));
    }

    @Test
    void preservesBenignChainedGoToDictionary() throws IOException {
        COSDictionary primary = goToAction();
        primary.setItem(COSName.getPDFName("Next"), goToAction());

        assertDoesNotThrow(() -> convert(pdfWithAnnotationAction(primary)));
    }

    @Test
    void preservesBenignChainedGoToArray() throws IOException {
        COSArray next = new COSArray();
        next.add(goToAction());
        next.add(goToAction());
        COSDictionary primary = goToAction();
        primary.setItem(COSName.getPDFName("Next"), next);

        assertDoesNotThrow(() -> convert(pdfWithAnnotationAction(primary)));
    }

    @Test
    void rejectsActionWithoutType() throws IOException {
        assertPolicyDenied(pdfWithAnnotationAction(new COSDictionary()));
    }

    @Test
    void rejectsGoToWithoutDestination() throws IOException {
        assertPolicyDenied(pdfWithAnnotationAction(action("GoTo")));
    }

    @Test
    void rejectsUriWithoutStringTarget() throws IOException {
        COSDictionary uri = action("URI");
        uri.setItem(COSName.getPDFName("URI"), COSName.getPDFName("not-a-string"));

        assertPolicyDenied(pdfWithAnnotationAction(uri));
    }

    @Test
    void rejectsUriWhenConfiguredAsAutomaticPageAction() throws IOException {
        assertPolicyDenied(pdfWithPageAdditionalAction(uriAction()));
    }

    @Test
    void rejectsMalformedAdditionalActionContainer() throws IOException {
        assertPolicyDenied(pdfWithPageAdditionalActions(new COSString("not-an-action-dictionary")));
    }

    @Test
    void rejectsMalformedAnnotationContainer() throws IOException {
        assertPolicyDenied(pdfWithMalformedAnnotations(new COSString("not-an-annotation-array")));
    }

    @Test
    void rejectsMalformedNextActionValue() throws IOException {
        COSDictionary primary = goToAction();
        primary.setItem(COSName.getPDFName("Next"), new COSString("not-an-action"));

        assertPolicyDenied(pdfWithAnnotationAction(primary));
    }

    @Test
    void rejectsChainedArrayContainingProhibitedAction() throws IOException {
        COSArray next = new COSArray();
        next.add(goToAction());
        next.add(action("SubmitForm"));
        COSDictionary primary = goToAction();
        primary.setItem(COSName.getPDFName("Next"), next);

        assertPolicyDenied(pdfWithAnnotationAction(primary));
    }

    @Test
    void rejectsActionChainBeyondPublicationDepthLimit() throws IOException {
        COSDictionary primary = goToAction();
        COSDictionary cursor = primary;
        for (int index = 1; index < 33; index++) {
            COSDictionary next = goToAction();
            cursor.setItem(COSName.getPDFName("Next"), next);
            cursor = next;
        }

        assertPolicyDenied(pdfWithAnnotationAction(primary));
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
                "trace-action-boundary",
                "fixture-source".getBytes(StandardCharsets.UTF_8),
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

    private static byte[] pdfWithAnnotationAction(COSDictionary action) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            COSDictionary annotation = new COSDictionary();
            annotation.setItem(COSName.TYPE, COSName.getPDFName("Annot"));
            annotation.setItem(COSName.SUBTYPE, COSName.getPDFName("Link"));
            annotation.setItem(COSName.getPDFName("A"), action);
            COSArray annotations = new COSArray();
            annotations.add(annotation);
            document.getPage(0).getCOSObject()
                    .setItem(COSName.getPDFName("Annots"), annotations);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithPageAdditionalAction(COSDictionary action) throws IOException {
        COSDictionary additionalActions = new COSDictionary();
        additionalActions.setItem(COSName.getPDFName("O"), action);
        return pdfWithPageAdditionalActions(additionalActions);
    }

    private static byte[] pdfWithPageAdditionalActions(COSBase additionalActions) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getPage(0).getCOSObject()
                    .setItem(COSName.getPDFName("AA"), additionalActions);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithMalformedAnnotations(COSBase annotations) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getPage(0).getCOSObject()
                    .setItem(COSName.getPDFName("Annots"), annotations);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static PDDocument onePageDocument() {
        PDDocument document = new PDDocument();
        document.addPage(new PDPage());
        return document;
    }

    private static COSDictionary goToAction() {
        COSDictionary action = action("GoTo");
        action.setItem(COSName.getPDFName("D"), COSName.getPDFName("section-one"));
        return action;
    }

    private static COSDictionary uriAction() {
        COSDictionary action = action("URI");
        action.setString(COSName.getPDFName("URI"), "https://example.invalid/clearfolio");
        return action;
    }

    private static COSDictionary action(String actionType) {
        COSDictionary action = new COSDictionary();
        action.setItem(COSName.getPDFName("S"), COSName.getPDFName(actionType));
        return action;
    }
}

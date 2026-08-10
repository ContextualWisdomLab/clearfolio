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
 * Behavior-level PDF action-policy regressions for converter output.
 *
 * <p>Network-independent conversion forbids dereferencing remote resources during
 * conversion, but it does not make explicit user navigation metadata executable.
 * The publication boundary therefore preserves direct internal navigation and
 * approved user-activated URI links while rejecting event-triggered additional
 * actions, executable behavior, malformed actions, chained-active actions, and
 * unknown action behavior.</p>
 */
class OfficeConversionPdfActionClassificationTest {

    @Test
    void adapterPreservesInternalDocumentGoToOpenAction() throws IOException {
        byte[] pdf = pdfWithDocumentOpenAction(goToAction());

        assertDoesNotThrow(() -> adapterReturning(pdf).convert(request()));
    }

    @Test
    void adapterPreservesExplicitInternalAnnotationGoToAction() throws IOException {
        byte[] pdf = pdfWithAnnotationAction(goToAction());

        assertDoesNotThrow(() -> adapterReturning(pdf).convert(request()));
    }

    @Test
    void adapterRejectsInternalPageAdditionalGoToAction() throws IOException {
        assertPolicyDenied(pdfWithPageAdditionalAction(goToAction()));
    }

    @Test
    void adapterRejectsInternalAnnotationAdditionalGoToAction() throws IOException {
        assertPolicyDenied(pdfWithAnnotationAdditionalAction(goToAction()));
    }

    @Test
    void adapterRejectsAnnotationSubmitFormAction() throws IOException {
        assertPolicyDenied(pdfWithAnnotationAction(action("SubmitForm")));
    }

    @Test
    void adapterRejectsAnnotationImportDataAction() throws IOException {
        assertPolicyDenied(pdfWithAnnotationAction(action("ImportData")));
    }

    @Test
    void adapterRejectsUnknownAnnotationActionType() throws IOException {
        assertPolicyDenied(pdfWithAnnotationAction(action("ClearfolioUnknown")));
    }

    @Test
    void adapterRejectsBenignPrimaryActionChainedToJavaScript() throws IOException {
        COSDictionary chainedAction = goToAction();
        chainedAction.setItem(COSName.getPDFName("Next"), action("JavaScript"));

        assertPolicyDenied(pdfWithAnnotationAction(chainedAction));
    }

    private static void assertPolicyDenied(byte[] pdf) {
        OfficeConversionException failure = assertThrows(
                OfficeConversionException.class,
                () -> adapterReturning(pdf).convert(request())
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
                "trace-action-policy",
                OfficeConversionTestSource.zipPackage("fixture-source"),
                1_000_000L,
                10
        );
    }

    private static byte[] pdfWithDocumentOpenAction(COSDictionary action) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("OpenAction"), action);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithPageAdditionalAction(COSDictionary action) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            COSDictionary additionalActions = new COSDictionary();
            additionalActions.setItem(COSName.getPDFName("O"), action);
            document.getPage(0).getCOSObject()
                    .setItem(COSName.getPDFName("AA"), additionalActions);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithAnnotationAdditionalAction(COSDictionary action) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            COSDictionary additionalActions = new COSDictionary();
            additionalActions.setItem(COSName.getPDFName("E"), action);
            COSDictionary annotation = linkAnnotation();
            annotation.setItem(COSName.getPDFName("AA"), additionalActions);
            attachAnnotation(document.getPage(0), annotation);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithAnnotationAction(COSDictionary action) throws IOException {
        try (PDDocument document = onePageDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            COSDictionary annotation = linkAnnotation();
            annotation.setItem(COSName.getPDFName("A"), action);
            attachAnnotation(document.getPage(0), annotation);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static PDDocument onePageDocument() {
        PDDocument document = new PDDocument();
        document.addPage(new PDPage());
        return document;
    }

    private static void attachAnnotation(PDPage page, COSDictionary annotation) {
        COSArray annotations = new COSArray();
        annotations.add(annotation);
        page.getCOSObject().setItem(COSName.getPDFName("Annots"), annotations);
    }

    private static COSDictionary linkAnnotation() {
        COSDictionary annotation = new COSDictionary();
        annotation.setItem(COSName.TYPE, COSName.getPDFName("Annot"));
        annotation.setItem(COSName.SUBTYPE, COSName.getPDFName("Link"));
        return annotation;
    }

    private static COSDictionary goToAction() {
        COSDictionary action = action("GoTo");
        action.setItem(COSName.getPDFName("D"), COSName.getPDFName("section-one"));
        return action;
    }

    private static COSDictionary action(String actionType) {
        COSDictionary action = new COSDictionary();
        action.setItem(COSName.getPDFName("S"), COSName.getPDFName(actionType));
        return action;
    }
}

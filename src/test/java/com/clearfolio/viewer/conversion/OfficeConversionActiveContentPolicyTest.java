package com.clearfolio.viewer.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    void adapterRejectsEmbeddedFileNameTreeWithoutExecutableAction() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithEmbeddedFilesNameTree());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsMalformedDocumentNameContainer() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithMalformedNameContainer());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsCatalogAssociatedFiles() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithCatalogAssociatedFiles());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsCatalogAdditionalActions() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithCatalogAdditionalActions());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsCatalogAutomaticGoToAdditionalAction() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithCatalogAutomaticGoToAdditionalAction());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsPageAdditionalActions() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithPageAdditionalActions());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsPageAutomaticGoToAdditionalAction() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithPageAutomaticGoToAdditionalAction());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsAnnotationJavaScriptAction() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithAnnotationAction(javascriptAction()));

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsAnnotationLaunchAction() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithAnnotationAction(launchAction()));

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsAnnotationAdditionalActions() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithAnnotationAdditionalActions());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterRejectsAnnotationAutomaticGoToAdditionalAction() throws IOException {
        OfficeConversionException failure = assertPolicyDenied(pdfWithAnnotationAutomaticGoToAdditionalAction());

        assertEquals(OfficeConversionFailureCode.POLICY_DENIED, failure.failureCode());
        assertEquals("conversion output contains prohibited active content", failure.getMessage());
    }

    @Test
    void adapterPreservesBenignAnnotationUriAction() throws IOException {
        byte[] pdf = pdfWithAnnotationAction(uriAction());
        OfficeConversionAdapter adapter = adapterReturning(pdf);

        assertDoesNotThrow(() -> adapter.convert(request()));
    }

    @Test
    void adapterAcceptsBenignEmptyDocumentNameDictionary() throws IOException {
        byte[] pdf = pdfWithEmptyNameDictionary();
        OfficeConversionAdapter adapter = adapterReturning(pdf);

        assertDoesNotThrow(() -> adapter.convert(request()));
    }

    private static OfficeConversionException assertPolicyDenied(byte[] pdf) {
        OfficeConversionAdapter adapter = adapterReturning(pdf);
        return assertThrows(
                OfficeConversionException.class,
                () -> adapter.convert(request())
        );
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

    private static byte[] pdfWithEmbeddedFilesNameTree() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());

            COSDictionary embeddedFilesTree = new COSDictionary();
            embeddedFilesTree.setItem(COSName.getPDFName("Names"), new COSArray());

            COSDictionary names = new COSDictionary();
            names.setItem(COSName.getPDFName("EmbeddedFiles"), embeddedFilesTree);
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("Names"), names);

            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithMalformedNameContainer() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("Names"), new COSString("not-a-name-dictionary"));
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithCatalogAssociatedFiles() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());

            COSDictionary fileSpecification = new COSDictionary();
            fileSpecification.setItem(COSName.TYPE, COSName.getPDFName("Filespec"));
            fileSpecification.setString(COSName.getPDFName("F"), "attachment.txt");
            fileSpecification.setItem(
                    COSName.getPDFName("AFRelationship"),
                    COSName.getPDFName("Data")
            );
            COSArray associatedFiles = new COSArray();
            associatedFiles.add(fileSpecification);
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("AF"), associatedFiles);

            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithCatalogAdditionalActions() throws IOException {
        return pdfWithCatalogAdditionalAction(javascriptAction());
    }

    private static byte[] pdfWithCatalogAutomaticGoToAdditionalAction() throws IOException {
        return pdfWithCatalogAdditionalAction(goToAction());
    }

    private static byte[] pdfWithCatalogAdditionalAction(COSDictionary action) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            COSDictionary additionalActions = new COSDictionary();
            additionalActions.setItem(COSName.getPDFName("WC"), action);
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("AA"), additionalActions);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithPageAdditionalActions() throws IOException {
        return pdfWithPageAdditionalAction(javascriptAction());
    }

    private static byte[] pdfWithPageAutomaticGoToAdditionalAction() throws IOException {
        return pdfWithPageAdditionalAction(goToAction());
    }

    private static byte[] pdfWithPageAdditionalAction(COSDictionary action) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            COSDictionary additionalActions = new COSDictionary();
            additionalActions.setItem(COSName.getPDFName("O"), action);
            page.getCOSObject().setItem(COSName.getPDFName("AA"), additionalActions);
            document.addPage(page);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithAnnotationAction(COSDictionary action) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();

            COSDictionary annotation = linkAnnotation();
            annotation.setItem(COSName.getPDFName("A"), action);

            COSArray annotations = new COSArray();
            annotations.add(annotation);
            page.getCOSObject().setItem(COSName.getPDFName("Annots"), annotations);
            document.addPage(page);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithAnnotationAdditionalActions() throws IOException {
        return pdfWithAnnotationAdditionalAction(javascriptAction());
    }

    private static byte[] pdfWithAnnotationAutomaticGoToAdditionalAction() throws IOException {
        return pdfWithAnnotationAdditionalAction(goToAction());
    }

    private static byte[] pdfWithAnnotationAdditionalAction(COSDictionary action) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();

            COSDictionary additionalActions = new COSDictionary();
            additionalActions.setItem(COSName.getPDFName("E"), action);
            COSDictionary annotation = linkAnnotation();
            annotation.setItem(COSName.getPDFName("AA"), additionalActions);

            COSArray annotations = new COSArray();
            annotations.add(annotation);
            page.getCOSObject().setItem(COSName.getPDFName("Annots"), annotations);
            document.addPage(page);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static COSDictionary linkAnnotation() {
        COSDictionary annotation = new COSDictionary();
        annotation.setItem(COSName.TYPE, COSName.getPDFName("Annot"));
        annotation.setItem(COSName.SUBTYPE, COSName.getPDFName("Link"));
        return annotation;
    }

    private static COSDictionary uriAction() {
        COSDictionary uriAction = new COSDictionary();
        uriAction.setItem(COSName.getPDFName("S"), COSName.getPDFName("URI"));
        uriAction.setString(COSName.getPDFName("URI"), "https://example.invalid/clearfolio");
        return uriAction;
    }

    private static COSDictionary goToAction() {
        COSDictionary goToAction = new COSDictionary();
        goToAction.setItem(COSName.getPDFName("S"), COSName.getPDFName("GoTo"));
        goToAction.setString(COSName.getPDFName("D"), "destination-one");
        return goToAction;
    }

    private static COSDictionary launchAction() {
        COSDictionary launchAction = new COSDictionary();
        launchAction.setItem(COSName.getPDFName("S"), COSName.getPDFName("Launch"));
        launchAction.setString(COSName.getPDFName("F"), "clearfolio-helper.exe");
        return launchAction;
    }

    private static byte[] pdfWithEmptyNameDictionary() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("Names"), new COSDictionary());
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

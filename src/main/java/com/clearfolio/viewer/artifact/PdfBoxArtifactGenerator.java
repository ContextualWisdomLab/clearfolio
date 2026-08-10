package com.clearfolio.viewer.artifact;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Development and test placeholder PDF generator backed by Apache PDFBox.
 *
 * <p>This class deliberately is not a Spring component. Its one-page metadata
 * output is useful for deterministic low-level tests and development fixtures,
 * but it is not document conversion and must never be selected by the
 * production application context as evidence of Office-format fidelity.
 * Production runtime wiring uses a qualified conversion adapter or fails
 * closed until one is configured.</p>
 */
public class PdfBoxArtifactGenerator implements PdfArtifactGenerator {

    @FunctionalInterface
    interface OutputTargetFactory {
        OutputTarget create();
    }

    private final OutputTargetFactory outputTargetFactory;

    /**
     * Creates a placeholder generator that writes to an in-memory buffer.
     */
    public PdfBoxArtifactGenerator() {
        this(OutputTarget::inMemory);
    }

    PdfBoxArtifactGenerator(OutputTargetFactory outputTargetFactory) {
        this.outputTargetFactory = Objects.requireNonNull(outputTargetFactory, "outputTargetFactory");
    }

    /**
     * Generates a deterministic metadata-only placeholder for development and tests.
     *
     * @param job conversion job whose metadata is rendered
     * @return placeholder PDF bytes; never evidence of source-document fidelity
     */
    @Override
    public byte[] generatePdf(ConversionJob job) {
        String fileName = pdfSafeText(job.getOriginalFileName());
        String contentHash = pdfSafeText(job.getContentHash());

        try (PDDocument document = new PDDocument();
             OutputTarget output = outputTargetFactory.create()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);

                content.showText("Clearfolio Viewer Preview");
                content.newLineAtOffset(0, -18);
                content.showText("docId: " + job.getJobId());
                content.newLineAtOffset(0, -18);
                content.showText("fileName: " + (fileName.isEmpty() ? "(unknown)" : fileName));
                content.newLineAtOffset(0, -18);
                content.showText("contentHash: " + (contentHash.isEmpty() ? "(unknown)" : contentHash));

                content.endText();
            }

            document.save(output.outputStream());
            return output.bytes();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to generate PDF artifact", ex);
        }
    }

    static String pdfSafeText(String value) {
        if (value == null) {
            return "";
        }

        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return "";
        }

        int firstBad = -1;
        for (int i = 0; i < stripped.length(); i++) {
            char ch = stripped.charAt(i);
            if (ch < 0x20 || ch > 0x7E) {
                firstBad = i;
                break;
            }
        }

        if (firstBad == -1) {
            return stripped;
        }

        StringBuilder normalized = new StringBuilder(stripped.length());
        normalized.append(stripped, 0, firstBad);
        for (int i = firstBad; i < stripped.length(); i++) {
            char ch = stripped.charAt(i);
            if (ch >= 0x20 && ch <= 0x7E) {
                normalized.append(ch);
            } else {
                normalized.append('?');
            }
        }

        return normalized.toString();
    }

    record OutputTarget(OutputStream outputStream, Supplier<byte[]> bytesSupplier) implements AutoCloseable {
        static OutputTarget inMemory() {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            return new OutputTarget(output, output::toByteArray);
        }

        byte[] bytes() {
            return bytesSupplier.get();
        }

        @Override
        public void close() throws IOException {
            outputStream.close();
        }
    }
}

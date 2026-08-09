package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Regression coverage ensuring unqualified transformed formats cannot be
 * reported as successful placeholder conversions by the production bean graph.
 */
class QualifiedConversionRequiredArtifactGeneratorTest {

    @Test
    void productionBeanGraphPrefersFailClosedGeneratorOverPlaceholderGenerator() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PdfBoxArtifactGenerator.class);
            context.registerBean(QualifiedConversionRequiredArtifactGenerator.class);
            context.refresh();

            PdfArtifactGenerator selected = context.getBean(PdfArtifactGenerator.class);
            assertInstanceOf(QualifiedConversionRequiredArtifactGenerator.class, selected);
        }
    }

    @Test
    void developmentPlaceholderIsNotAProductionScannedComponent() {
        assertFalse(PdfBoxArtifactGenerator.class.isAnnotationPresent(Component.class));
    }

    @Test
    void rejectsGenerationUntilQualifiedOfficeAdapterIsConfigured() {
        QualifiedConversionRequiredArtifactGenerator generator =
                new QualifiedConversionRequiredArtifactGenerator();
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "tenant-a",
                "subject-a",
                "buyer-deck.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "content-hash",
                128L,
                3
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> generator.generatePdf(job)
        );

        assertTrue(error.getMessage().contains("qualified document converter"));
    }
}

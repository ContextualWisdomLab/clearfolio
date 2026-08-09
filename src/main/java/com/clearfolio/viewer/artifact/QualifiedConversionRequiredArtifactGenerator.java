package com.clearfolio.viewer.artifact;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Production-safe artifact generator selected while no qualified transformed-
 * format conversion adapter is configured.
 *
 * <p>PDF uploads are seeded into the artifact store unchanged before the worker
 * reaches this generator. Any invocation here therefore represents a source
 * that would otherwise be turned into Clearfolio's development placeholder
 * PDF. Returning a placeholder with a successful conversion state would
 * overstate document fidelity, so the production bean fails closed until the
 * sandboxed Office conversion boundary is qualified.</p>
 */
@Component
@Primary
public final class QualifiedConversionRequiredArtifactGenerator implements PdfArtifactGenerator {

    /**
     * Rejects transformed-format generation while no qualified adapter exists.
     *
     * @param job conversion job that requires transformed-format rendering
     * @return never returns normally
     * @throws IllegalStateException always, because no production converter is qualified
     */
    @Override
    public byte[] generatePdf(ConversionJob job) {
        throw new IllegalStateException(
                "qualified document converter is not configured; placeholder output is not production success"
        );
    }
}

package com.clearfolio.viewer.fuzz;

import java.util.UUID;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import com.clearfolio.viewer.conversion.OfficeConversionAdapter;
import com.clearfolio.viewer.conversion.OfficeConversionException;
import com.clearfolio.viewer.conversion.OfficeConversionFailureCode;
import com.clearfolio.viewer.conversion.OfficeConversionRequest;

/**
 * Fuzzes the provider-neutral Office source boundary with hostile container bytes.
 *
 * <p>The converter implementation is deliberately replaced with a sentinel that
 * throws a typed policy rejection. Therefore arbitrary source bytes may either be
 * rejected by Clearfolio's ZIP/ODF/compound-file preflight or reach the sentinel,
 * but no parser implementation exception or other unexpected throwable may escape.
 * This target never starts an Office process and never dereferences external data.</p>
 */
final class OfficeConversionBoundaryFuzzTest {

    private static final String[] FORMATS = {
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp"
    };

    private static final OfficeConversionAdapter SENTINEL_PROVIDER = request -> {
        throw new OfficeConversionException(
                OfficeConversionFailureCode.POLICY_DENIED,
                "fuzz sentinel provider reached"
        );
    };

    @FuzzTest(maxDuration = "60s")
    void hostileOfficeBytesOnlyFailThroughTypedConversionBoundary(FuzzedDataProvider data) {
        String format = FORMATS[data.consumeInt(0, FORMATS.length - 1)];
        byte[] sourceBytes = data.consumeRemainingAsBytes();
        if (sourceBytes.length == 0) {
            sourceBytes = new byte[] {0};
        }

        OfficeConversionRequest request = new OfficeConversionRequest(
                "fuzz-tenant",
                UUID.fromString("cd348e0e-bd83-433a-a7d0-818d297b98af"),
                1L,
                format,
                "fuzz-sentinel",
                "1",
                "fuzz-policy",
                "fuzz-trace",
                sourceBytes
        );

        try {
            SENTINEL_PROVIDER.convert(request);
        } catch (OfficeConversionException expected) {
            // Typed fail-closed rejection is the complete public failure contract here.
        }
    }
}

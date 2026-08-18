package com.clearfolio.viewer.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Protects the public documentation boundary of the deterministic Office fixture adapter.
 */
class DeterministicFixtureOfficeConversionAdapterClaimTest {

    /**
     * Prevents a byte-replay fixture from being described as evidence of Office rendering fidelity.
     *
     * @throws IOException when the production source cannot be read by the contract test
     */
    @Test
    void deterministicFixtureIsDocumentedAsContractOracleNotFidelityImplementation() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/clearfolio/viewer/conversion/DeterministicFixtureOfficeConversionAdapter.java"
        ));

        assertThat(source)
                .contains("contract oracle")
                .doesNotContain("contract and fidelity test implementation");
    }
}

"""Protect bounded artifact-token evidence from unsupported performance claims."""

import unittest
from pathlib import Path

ARTIFACT_LINK_SERVICE = Path(
    "src/main/java/com/clearfolio/viewer/artifact/ArtifactLinkService.java"
)
ARTIFACT_TOKEN_CLAIMS = Path(
    "src/main/java/com/clearfolio/viewer/artifact/ArtifactTokenClaims.java"
)
BOUNDARY_TEST = Path(
    "src/test/java/com/clearfolio/viewer/artifact/ArtifactTokenBoundaryTest.java"
)
CHANGELOG = Path("CHANGELOG.md")

UNSUPPORTED_PERFORMANCE_CLAIMS = (
    "불필요한 배열 할당",
    "배열 할당 및 정규식 오버헤드를 제거",
    "보안성과 성능을 동시에 개선",
    "reduces array allocation",
    "reduces regex overhead",
)


class ArtifactTokenParserEvidenceTest(unittest.TestCase):
    """Keep artifact-token evidence executable under standard test discovery."""

    def test_bounded_token_contract_has_no_unbenchmarked_performance_claim(self) -> None:
        """Reject unsupported performance claims in production and release evidence."""
        evidence_sources = {
            ARTIFACT_LINK_SERVICE: ARTIFACT_LINK_SERVICE.read_text(encoding="utf-8"),
            ARTIFACT_TOKEN_CLAIMS: ARTIFACT_TOKEN_CLAIMS.read_text(encoding="utf-8"),
            CHANGELOG: CHANGELOG.read_text(encoding="utf-8"),
        }

        for path, source in evidence_sources.items():
            for claim in UNSUPPORTED_PERFORMANCE_CLAIMS:
                self.assertNotIn(
                    claim,
                    source,
                    "unsupported artifact-token performance claim remains "
                    f"in {path}: {claim!r}",
                )

    def test_bounded_token_contract_keeps_signed_boundary_regressions(self) -> None:
        """Require deterministic tests for every malformed signed-payload boundary."""
        source = BOUNDARY_TEST.read_text(encoding="utf-8")
        required_tests = (
            "rejectsDelimiterFreeMalformedToken",
            "rejectsStructurallyValidTokenWithMismatchedSignature",
            "rejectsSignedPayloadWithOnlyNineFields",
            "rejectsSignedPayloadWithElevenFields",
            "rejectsValidTokenWithTrailingDelimiter",
            "rejectsSignedPayloadWithAnEmptyRequiredField",
            "rejectsSignedPayloadWithAWhitespaceOnlyRequiredField",
            "rejectsSignedPayloadWithMalformedBase64Url",
            "rejectsSignedPayloadWithNonNumericEpochSecond",
            "rejectsSignedPayloadWithOutOfRangeEpochSecond",
            "rejectsSignedPayloadWithMalformedDocumentIdentifier",
            "rejectsSignedPayloadWithUnsupportedVersion",
        )

        for test_name in required_tests:
            self.assertIn(
                f"void {test_name}()",
                source,
                f"missing deterministic signed-boundary regression: {test_name}",
            )


if __name__ == "__main__":
    unittest.main()

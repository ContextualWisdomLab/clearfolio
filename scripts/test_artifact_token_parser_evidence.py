"""Protect artifact-token parser evidence from unsupported performance claims."""

from pathlib import Path

ARTIFACT_LINK_SERVICE = Path(
    "src/main/java/com/clearfolio/viewer/artifact/ArtifactLinkService.java"
)
BOUNDARY_TEST = Path(
    "src/test/java/com/clearfolio/viewer/artifact/ArtifactTokenManualParserBoundaryTest.java"
)


def test_manual_parser_has_no_unbenchmarked_performance_claim() -> None:
    """Require benchmark evidence before claiming allocation or regex improvements."""
    source = ARTIFACT_LINK_SERVICE.read_text(encoding="utf-8")

    unsupported_claims = (
        "불필요한 배열 할당",
        "정규식 오버헤드",
        "reduces array allocation",
        "reduces regex overhead",
    )

    for claim in unsupported_claims:
        assert claim not in source, (
            f"unsupported artifact-token parser performance claim remains: {claim!r}"
        )


def test_manual_parser_keeps_signed_boundary_regressions() -> None:
    """Require deterministic tests for every malformed signed-payload boundary."""
    source = BOUNDARY_TEST.read_text(encoding="utf-8")
    required_tests = (
        "rejectsSignedPayloadWithOnlyNineFields",
        "rejectsSignedPayloadWithElevenFields",
        "rejectsSignedPayloadWithAnEmptyRequiredField",
        "rejectsSignedPayloadWithMalformedBase64Url",
        "rejectsSignedPayloadWithNonNumericEpochSecond",
        "rejectsSignedPayloadWithOutOfRangeEpochSecond",
        "rejectsSignedPayloadWithMalformedDocumentIdentifier",
        "rejectsSignedPayloadWithUnsupportedVersion",
    )

    for test_name in required_tests:
        assert f"void {test_name}()" in source, (
            f"missing deterministic signed-boundary regression: {test_name}"
        )

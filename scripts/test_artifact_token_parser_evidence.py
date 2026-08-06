"""Protect artifact-token parser evidence from unsupported performance claims."""

from pathlib import Path

ARTIFACT_LINK_SERVICE = Path(
    "src/main/java/com/clearfolio/viewer/artifact/ArtifactLinkService.java"
)
BOUNDARY_TEST = Path(
    "src/test/java/com/clearfolio/viewer/artifact/ArtifactTokenManualParserBoundaryTest.java"
)
CHANGELOG = Path("CHANGELOG.md")

UNSUPPORTED_PERFORMANCE_CLAIMS = (
    "불필요한 배열 할당",
    "배열 할당 및 정규식 오버헤드를 제거",
    "보안성과 성능을 동시에 개선",
    "reduces array allocation",
    "reduces regex overhead",
)


def test_manual_parser_has_no_unbenchmarked_performance_claim() -> None:
    """Reject unsupported performance claims in production and release evidence."""
    evidence_sources = {
        ARTIFACT_LINK_SERVICE: ARTIFACT_LINK_SERVICE.read_text(encoding="utf-8"),
        CHANGELOG: CHANGELOG.read_text(encoding="utf-8"),
    }

    for path, source in evidence_sources.items():
        for claim in UNSUPPORTED_PERFORMANCE_CLAIMS:
            assert claim not in source, (
                "unsupported artifact-token parser performance claim remains "
                f"in {path}: {claim!r}"
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

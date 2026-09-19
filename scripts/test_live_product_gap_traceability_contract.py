#!/usr/bin/env python3
"""Keep canonical documentation aligned with newly accepted Clearfolio product gaps."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    """Return one repository document as normalized lowercase text."""

    return " ".join(
        (REPOSITORY_ROOT / relative_path)
        .read_text(encoding="utf-8")
        .lower()
        .split()
    )


class LiveProductGapTraceabilityContractTest(unittest.TestCase):
    """Require current buyer-visible gaps and bounded remediation slices to be traceable."""

    def test_traceability_tracks_production_workspace_demo_authority_gap(self) -> None:
        """Require issue #317 and its bounded branding slice to remain explicit."""

        traceability = read_text("docs/TRACEABILITY.md")
        for required in (
            "issue #317",
            "production workspace",
            "buyer-demo",
            "#318",
            "branding",
        ):
            with self.subTest(required=required):
                self.assertIn(required, traceability)

    def test_traceability_separates_openapi_license_integrity_from_schema_completeness(self) -> None:
        """Require #316 to stay a bounded Apache-2.0 integrity slice beneath issue #315."""

        traceability = read_text("docs/TRACEABILITY.md")
        for required in (
            "issue #315",
            "#316",
            "apache-2.0",
            "bounded integrity",
            "partial",
        ):
            with self.subTest(required=required):
                self.assertIn(required, traceability)

    def test_traceability_tracks_runtime_credential_registry_gap(self) -> None:
        """Require issue #319 to stay separate from the active key-readiness slice."""

        traceability = read_text("docs/TRACEABILITY.md")
        for required in (
            "issue #319",
            "credential registry",
            "environment",
            "#313",
        ):
            with self.subTest(required=required):
                self.assertIn(required, traceability)

    def test_traceability_tracks_current_clean_replacements_and_main_advancement(self) -> None:
        """Prevent closed predecessor PR identities from remaining the canonical live mapping."""

        traceability = read_text("docs/TRACEABILITY.md")
        for required in (
            "protected `main` at `55d7ae8647208e301f282350f076eeddaba61d11`",
            "direct-download signed delivery | `implemented_on_main`",
            "privacy-safe hmac audit pseudonymization | `implemented_on_main`",
            "exact-head ci/test evidence | `implemented_on_main`",
            "issue #324; current `active_pr` #334",
            "issue #327; current `active_pr` #338",
            "issue #329; current `active_pr` #389",
            "issue #315; current `active_pr` #337",
            "#325",
            "superseded",
            "#328",
            "#330",
        ):
            with self.subTest(required=required):
                self.assertIn(required, traceability)

    def test_traceability_tracks_current_accessibility_and_logging_reconciliations(self) -> None:
        """Require the newly verified current-base slices to replace historical/deferred prose."""

        traceability = read_text("docs/TRACEABILITY.md")
        assessment = read_text("docs/DOCUMENTATION_ASSESSMENT.md")

        for required in (
            "nested-safe accessible asynchronous controls | `active_pr`; current #264",
            "deterministic single logging runtime binding | `active_pr`; issue #320; current #340",
            "dependency-free node",
            "spring-jcl",
            "commons-logging",
        ):
            with self.subTest(document="traceability", required=required):
                self.assertIn(required, traceability)

        for required in (
            "current #264",
            "current #340",
            "#268",
            "superseded",
        ):
            with self.subTest(document="assessment", required=required):
                self.assertIn(required, assessment)
        self.assertNotIn("only #268 remains unreconciled", assessment)

    def test_canonical_docs_reject_stale_268_and_direct_download_maturity(self) -> None:
        """Require shipped direct download and superseded deletion ancestry to be classified truthfully."""

        uml = read_text("docs/UML.md")
        data_model = read_text("docs/DATA_MODEL.md")
        assessment = read_text("docs/DOCUMENTATION_ASSESSMENT.md")

        self.assertIn("direct conversion-job download", uml)
        self.assertIn("`implemented_on_main`", uml)
        self.assertNotIn("direct conversion-job download alignment is an `active_pr`", uml)

        for document_name, document in (("uml", uml), ("data_model", data_model)):
            with self.subTest(document=document_name):
                self.assertIn("#268", document)
                self.assertIn("superseded", document)
                self.assertIn("partial", document)
                self.assertNotIn("`active_pr` #268", document)

        self.assertIn("#268", assessment)
        self.assertIn("superseded", assessment)
        self.assertNotIn("only #268 remains unreconciled", assessment)

    def test_all_canonical_docs_reject_superseded_268_and_stale_download_maturity(self) -> None:
        """Keep lifecycle and signed-download maturity consistent across canonical docs."""

        supersession_documents = (
            "docs/PRD.md",
            "docs/TRD.md",
            "ARCHITECTURE.md",
            "docs/OPERABILITY.md",
            "docs/API_CONTRACT.md",
            "docs/THREAT_MODEL.md",
            "docs/MIGRATION_ROLLBACK.md",
            "docs/TEST_STRATEGY.md",
            "docs/adr/README.md",
            "docs/adr/0003-tenant-security-and-durable-evidence.md",
            "docs/adr/0004-immutable-document-lifecycle.md",
            "docs/DOCUMENTATION_ASSESSMENT.md",
            "docs/ACQUISITION_DILIGENCE.md",
        )
        for relative_path in supersession_documents:
            document = read_text(relative_path)
            with self.subTest(document=relative_path):
                self.assertIn("#268", document)
                self.assertIn("superseded", document)
                self.assertNotIn("`active_pr` #268", document)

        signed_download_documents = (
            "docs/PRD.md",
            "docs/TRD.md",
            "ARCHITECTURE.md",
            "docs/THREAT_MODEL.md",
            "docs/adr/README.md",
        )
        for relative_path in signed_download_documents:
            document = read_text(relative_path)
            with self.subTest(document=relative_path):
                self.assertIn("implemented_on_main", document)
                self.assertNotIn("direct-download hardening `active_pr`", document)
                self.assertNotIn("direct-download alignment `active_pr`", document)
                self.assertNotIn(
                    "direct conversion-job download endpoint is currently under `active_pr`",
                    document,
                )

        traceability = read_text("docs/TRACEABILITY.md")
        self.assertIn("assessment date: 2026-09-20", traceability)

    def test_assessment_exposes_current_api_workspace_and_credential_gaps(self) -> None:
        """Require the fitness assessment to expose current executable product/security gaps."""

        assessment = read_text("docs/DOCUMENTATION_ASSESSMENT.md")
        for required in (
            "issue #315",
            "issue #317",
            "issue #319",
            "production workspace",
            "versioned api",
            "credential registry",
        ):
            with self.subTest(required=required):
                self.assertIn(required, assessment)


if __name__ == "__main__":
    unittest.main()

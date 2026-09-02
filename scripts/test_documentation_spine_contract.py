#!/usr/bin/env python3
"""Verify that Clearfolio's canonical architecture documentation stays coherent."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
CANONICAL_DOCUMENTS = (
    "docs/PRD.md",
    "docs/TRD.md",
    "ARCHITECTURE.md",
    "SECURITY.md",
    "docs/DATA_MODEL.md",
    "docs/UML.md",
    "docs/API_CONTRACT.md",
    "docs/THREAT_MODEL.md",
    "docs/TEST_STRATEGY.md",
    "docs/OPERABILITY.md",
    "docs/TRACEABILITY.md",
    "docs/RESEARCH_TRACEABILITY.md",
    "docs/DOCUMENTATION_ASSESSMENT.md",
    "docs/engineering/acceptance-criteria.md",
    "docs/adr/README.md",
)
SUPPLEMENTAL_RELEASE_DOCUMENTS = (
    "docs/FIDELITY_ACCEPTANCE.md",
    "docs/MIGRATION_ROLLBACK.md",
    "docs/RELEASE_ACCEPTANCE.md",
)
ADR_FILES = tuple(
    f"docs/adr/{number:04d}-{slug}.md"
    for number, slug in (
        (1, "standalone-msa-ownership"),
        (2, "tenant-artifact-authorization"),
        (3, "audit-pseudonymization-key-separation"),
        (4, "durable-lifecycle-generation-fencing"),
        (5, "deterministic-conversion-fidelity"),
        (6, "liveness-readiness-separation"),
        (7, "exact-head-live-base-evidence"),
        (8, "central-vs-local-automation-authority"),
        (9, "work-conserving-rca-loop"),
        (10, "release-provenance-fidelity-gate"),
        (11, "thin-scheduler-control-plane"),
        (12, "scheduler-execution-receipts"),
    )
)
GUIDE_FILES = ("README.md", "AGENTS.md", "CLAUDE.md")
FINAL_VERIFY_COMMAND = "mvn -B --no-transfer-progress verify"


def read_text(relative_path: str) -> str:
    """Return one repository text file using strict UTF-8 decoding."""

    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


class DocumentationSpineContractTest(unittest.TestCase):
    """Protect the canonical PRD/TRD/architecture/ADR/UML/ERD document graph."""

    def test_canonical_documents_exist(self) -> None:
        """Require every canonical document and detailed ADR to remain present."""

        for relative_path in (*CANONICAL_DOCUMENTS, *SUPPLEMENTAL_RELEASE_DOCUMENTS, *ADR_FILES):
            with self.subTest(path=relative_path):
                self.assertTrue((REPOSITORY_ROOT / relative_path).is_file())

    def test_architecture_links_the_canonical_spine(self) -> None:
        """Require root architecture to remain the discoverable documentation index."""

        architecture = read_text("ARCHITECTURE.md")
        for relative_path in (*CANONICAL_DOCUMENTS, *SUPPLEMENTAL_RELEASE_DOCUMENTS):
            if relative_path == "ARCHITECTURE.md":
                continue
            with self.subTest(path=relative_path):
                self.assertIn(f"`{relative_path}`", architecture)

    def test_public_guides_link_product_requirements_and_decisions(self) -> None:
        """Keep README and agent guides anchored to the same canonical spine."""

        required_links = ("docs/PRD.md", "docs/TRD.md", "docs/adr/README.md")
        for relative_path in GUIDE_FILES:
            text = read_text(relative_path)
            for canonical_path in required_links:
                with self.subTest(path=relative_path, canonical=canonical_path):
                    self.assertIn(canonical_path, text)

    def test_claude_authority_classification_matches_repository_guidance(self) -> None:
        """Keep canonical and supplemental documentation authority unambiguous for agents."""

        claude = read_text("CLAUDE.md")
        self.assertIn("docs/DOCUMENTATION_ASSESSMENT.md", claude)
        self.assertIn("Supplemental release and research authorities", claude)
        for supplemental in (
            "docs/FIDELITY_ACCEPTANCE.md",
            "docs/MIGRATION_ROLLBACK.md",
            "docs/RELEASE_ACCEPTANCE.md",
            "docs/RESEARCH_TRACEABILITY.md",
        ):
            with self.subTest(supplemental=supplemental):
                self.assertIn(supplemental, claude)

    def test_claude_premerge_contract_requires_protection_and_independent_approval(self) -> None:
        """Prevent advisory/model evidence from silently replacing protected merge governance."""

        claude = read_text("CLAUDE.md").lower()
        for required in (
            "protected `main` branch rules",
            "all required checks",
            "required reviewer count",
            "qualifying independent approval",
            "automated comments, check results, and model output do not count as approval",
        ):
            with self.subTest(required=required):
                self.assertIn(required, claude)

    def test_public_guides_use_final_maven_verify_command(self) -> None:
        """Prevent agent/user guidance from regressing to a partial final test command."""

        for relative_path in GUIDE_FILES:
            with self.subTest(path=relative_path):
                self.assertIn(FINAL_VERIFY_COMMAND, read_text(relative_path))

    def test_readme_does_not_restore_stale_mvp_runtime_claims(self) -> None:
        """Reject the historical MVP/in-memory-artifact summary as current product truth."""

        readme = read_text("README.md").lower()
        self.assertNotIn("contains the mvp backend", readme)
        self.assertNotIn("asynchronous conversion that produces an in-memory pdf artifact", readme)
        self.assertIn("development/demo one-page placeholder", readme)

    def test_security_policy_matches_current_trust_boundaries(self) -> None:
        """Require root security guidance to expose current authorization and privacy boundaries."""

        security = " ".join(read_text("SECURITY.md").lower().split())
        for required in (
            "signed artifact",
            "same-tenant",
            "hmac",
            "pseudonym",
            "purpose",
            "security advisories",
        ):
            with self.subTest(required=required):
                self.assertIn(required, security)
        self.assertIn("pseudonymized", security)
        self.assertIn("personal data", security)
        self.assertNotIn("permission alone authorizes document bytes", security)

    def test_changelog_has_one_unreleased_section(self) -> None:
        """Keep current unreleased changes in one reviewable section."""

        changelog = read_text("CHANGELOG.md")
        self.assertEqual(1, len(re.findall(r"^## \[Unreleased\]$", changelog, flags=re.MULTILINE)))

    def test_adr_index_links_every_detailed_decision(self) -> None:
        """Require the ADR index to link exactly the canonical detailed ADR files."""

        index = read_text("docs/adr/README.md")
        linked = set(re.findall(r"\]\((\d{4}[-a-z0-9]+\.md)\)", index))
        expected = {Path(path).name for path in ADR_FILES}
        self.assertEqual(expected, linked)

    def test_documents_distinguish_main_active_and_planned_work(self) -> None:
        """Prevent target architecture or active PR work from masquerading as shipped."""

        required_labels = (
            "IMPLEMENTED_ON_MAIN",
            "ACTIVE_PR",
            "PARTIAL",
            "ACCEPTED_ARCHITECTURE",
            "PLANNED",
        )
        for relative_path in ("docs/PRD.md", "docs/TRD.md", "ARCHITECTURE.md"):
            text = read_text(relative_path)
            for label in required_labels:
                with self.subTest(path=relative_path, label=label):
                    self.assertIn(label, text)

    def test_documentation_assessment_uses_canonical_status_labels(self) -> None:
        """Keep machine-readable documentation fitness labels canonical."""

        assessment = read_text("docs/DOCUMENTATION_ASSESSMENT.md")
        self.assertIn("DESIGN_SUFFICIENT", assessment)
        self.assertIn("PROTECTED_MAIN_SUFFICIENT", assessment)
        self.assertNotIn("DESIGN-SUFFICIENT", assessment)
        self.assertNotIn("PROTECTED-MAIN-SUFFICIENT", assessment)

    def test_test_strategy_runs_canonical_script_acceptance(self) -> None:
        """Require documented acceptance to execute the documentation contract and all script tests."""

        strategy = read_text("docs/TEST_STRATEGY.md")
        self.assertIn("python3 scripts/test_documentation_spine_contract.py", strategy)
        self.assertIn("python3 -m unittest discover -s scripts", strategy)

    def test_exact_head_adr_defines_atomic_merge_preconditions(self) -> None:
        """Require merge-time head/base movement to fail closed and trigger revalidation."""

        adr = read_text("docs/adr/0007-exact-head-live-base-evidence.md").lower()
        for required in (
            "expected source-head sha",
            "supported base-tip sha",
            "merge time",
            "reject",
            "fresh-state revalidation",
        ):
            with self.subTest(required=required):
                self.assertIn(required, adr)

    def test_automation_adr_names_single_active_pr_writer_and_pinning(self) -> None:
        """Prevent duplicate ACTIVE_PR writers and unpinned local agent execution."""

        adr = read_text("docs/adr/0008-central-vs-local-automation-authority.md").lower()
        for required in (
            "only actor permitted to process `active_pr`",
            "central fleet loops",
            "clearfolio disabled",
            "immutable",
            "pinned",
        ):
            with self.subTest(required=required):
                self.assertIn(required, adr)

    def test_placeholder_conversion_is_not_documented_as_office_fidelity(self) -> None:
        """Keep placeholder PDF generation outside supported Office conversion claims."""

        combined = "\n".join(
            read_text(path)
            for path in (
                "docs/PRD.md",
                "docs/TRD.md",
                "ARCHITECTURE.md",
                "docs/adr/0005-deterministic-conversion-fidelity.md",
                "docs/FIDELITY_ACCEPTANCE.md",
            )
        ).lower()
        self.assertIn("placeholder", combined)
        self.assertIn("not", combined)
        self.assertIn("office", combined)
        self.assertIn("fidelity", combined)
        self.assertIn("development_placeholder", combined)

    def test_fidelity_contract_requires_realistic_deterministic_evidence(self) -> None:
        """Prevent extension/HTTP success from becoming a production format claim."""

        fidelity = read_text("docs/FIDELITY_ACCEPTANCE.md").lower()
        for required in (
            "realistic fixtures",
            "authorized or redistributable",
            "deterministic rendering",
            "active content",
            "network access is not required by default",
            "output digests",
            "unsupported",
            "failed",
            "development_placeholder",
        ):
            with self.subTest(required=required):
                self.assertIn(required, fidelity)

    def test_office_converter_isolation_is_a_durable_decision(self) -> None:
        """Prevent an adapter library capability from collapsing the API trust boundary."""

        adr = read_text("docs/adr/0005-deterministic-conversion-fidelity.md").lower()
        for required in (
            "provider-neutral `office_conversion_adapter`",
            "does **not** run inside the clearfolio api-container trust boundary",
            "no inherited api/cloud credentials",
            "deny-by-default outbound network access",
            "a successful converter process exit alone is never a production support oracle",
        ):
            with self.subTest(required=required):
                self.assertIn(required, adr)

    def test_release_contract_binds_exact_source_and_artifact_evidence(self) -> None:
        """Require one integrated exact-head release gate rather than green-PR inference."""

        release = read_text("docs/RELEASE_ACCEPTANCE.md")
        for required in (
            "exact protected source commit SHA",
            "source_head_sha",
            "live_base_tip_sha",
            "docs/FIDELITY_ACCEPTANCE.md",
            "docs/MIGRATION_ROLLBACK.md",
            "SBOM",
            "independent non-author formal approval",
        ):
            with self.subTest(required=required):
                self.assertIn(required, release)

    def test_migration_contract_does_not_invent_current_database_persistence(self) -> None:
        """Keep rollback guidance honest about process-local job state and future SQL."""

        migration = read_text("docs/MIGRATION_ROLLBACK.md")
        self.assertIn("Conversion-job state is process-local/in-memory", migration)
        self.assertIn("Future SQL persistence", migration)
        self.assertIn("must not be reintroduced", migration)
        self.assertIn("must not re-enable a known authorization bypass", migration)

    def test_automation_contract_names_required_agent_secret_boundary(self) -> None:
        """Keep autonomous development on OpenCode/NVIDIA rather than Copilot credentials."""

        combined = "\n".join(
            read_text(path)
            for path in (
                "docs/PRD.md",
                "docs/TRD.md",
                "ARCHITECTURE.md",
                "docs/adr/0008-central-vs-local-automation-authority.md",
            )
        )
        self.assertIn("NVIDIA_NIM_API_KEY", combined)
        self.assertIn("COPILOT_GITHUB_TOKEN", combined)
        self.assertIn("OpenCode", combined)

    def test_work_conserving_contract_forbids_report_as_completion(self) -> None:
        """Keep RCA and documentation outcomes as intermediate work while safe work exists."""

        adr = read_text("docs/adr/0009-work-conserving-rca-loop.md").lower()
        self.assertIn("work-conserving", adr)
        self.assertIn("two fresh exit sweeps", adr)
        self.assertIn("blocker report", adr)
        self.assertIn("not a successful run", adr)

    def test_diagram_fences_are_balanced(self) -> None:
        """Reject malformed Markdown diagram fences in canonical diagram-bearing docs."""

        for relative_path in ("ARCHITECTURE.md", "docs/UML.md", "docs/DATA_MODEL.md", "docs/THREAT_MODEL.md"):
            text = read_text(relative_path)
            with self.subTest(path=relative_path):
                self.assertEqual(text.count("```") % 2, 0)


if __name__ == "__main__":
    unittest.main()

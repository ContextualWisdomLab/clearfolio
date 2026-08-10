#!/usr/bin/env python3
"""Require canonical documentation for diagnosable scheduler continuation."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    """Return one repository document as normalized lowercase text."""

    document_path = REPOSITORY_ROOT / relative_path
    if not document_path.exists():
        return ""
    return " ".join(document_path.read_text(encoding="utf-8").lower().split())


class SchedulerExecutionReceiptDocumentationContractTest(unittest.TestCase):
    """Keep scheduler failures diagnosable without inventing hidden causes."""

    def test_adr_index_links_execution_receipt_decision(self) -> None:
        """Require a dedicated decision beyond the thin-control-plane ADR."""

        adr_index = read_text("docs/adr/README.md")
        for required in (
            "0012-scheduler-execution-receipts.md",
            "execution receipt",
            "budget continuation",
        ):
            with self.subTest(required=required):
                self.assertIn(required, adr_index)

    def test_execution_receipt_adr_covers_failure_and_recovery(self) -> None:
        """Require exact failure-envelope, checkpoint, and supersession semantics."""

        adr = read_text("docs/adr/0012-scheduler-execution-receipts.md")
        for required in (
            "status: proposed",
            "automation checkpoint",
            "action receipt",
            "failure envelope",
            "continuation handoff",
            "budget continuation",
            "generic scheduled-task error",
            "do not invent",
            "rollback",
            "supersession",
        ):
            with self.subTest(required=required):
                self.assertIn(required, adr)

    def test_data_model_marks_scheduler_receipts_conceptual_or_external(self) -> None:
        """Prevent the ERD from inventing Clearfolio-owned scheduler persistence."""

        data_model = read_text("docs/DATA_MODEL.md")
        for required in (
            "automation_checkpoint",
            "action_receipt",
            "failure_envelope",
            "continuation_handoff",
            "conceptual",
            "external",
        ):
            with self.subTest(required=required):
                self.assertIn(required, data_model)

    def test_uml_and_operability_show_resumable_execution_flow(self) -> None:
        """Require a visible flow from admission through receipt or continuation."""

        uml = read_text("docs/UML.md")
        operability = read_text("docs/OPERABILITY.md")
        for required in (
            "schedule",
            "admission",
            "fresh queue",
            "atomic action",
            "action receipt",
            "budget continuation",
            "failure envelope",
        ):
            with self.subTest(document="uml", required=required):
                self.assertIn(required, uml)
        for required in (
            "issue #331",
            "generic scheduled-task error",
            "last safe checkpoint",
            "budget continuation",
            "controlled failure",
            "fresh github state",
        ):
            with self.subTest(document="operability", required=required):
                self.assertIn(required, operability)

    def test_traceability_and_assessment_expose_live_scheduler_gap(self) -> None:
        """Require issue #331 and its non-shipped maturity to stay explicit."""

        traceability = read_text("docs/TRACEABILITY.md")
        assessment = read_text("docs/DOCUMENTATION_ASSESSMENT.md")
        for required in (
            "issue #331",
            "scheduler execution receipt",
            "planned",
        ):
            with self.subTest(document="traceability", required=required):
                self.assertIn(required, traceability)
        for required in (
            "issue #331",
            "execution receipt",
            "protected_main_insufficient",
        ):
            with self.subTest(document="assessment", required=required):
                self.assertIn(required, assessment)

    def test_assessment_states_conversation_coverage_and_enforcement_limits(self) -> None:
        """Require an explicit verdict for the repeated early-stop and documentation request."""

        assessment = read_text("docs/DOCUMENTATION_ASSESSMENT.md")
        for required in (
            "conversation decision coverage",
            "conversation_coverage_sufficient",
            "control_enforcement_incomplete",
            "prompt repair earns zero completion credit",
            "same-invocation substantive action",
            "multi-lane rotation",
            "two consecutive fresh exit sweeps",
            "repository-scoped conversation decisions",
            "other contextualwisdomlab projects are not imported",
        ):
            with self.subTest(required=required):
                self.assertIn(required, assessment)


if __name__ == "__main__":
    unittest.main()

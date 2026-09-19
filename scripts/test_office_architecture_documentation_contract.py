#!/usr/bin/env python3
"""Require truthful Office-adapter architecture and logical data-model coverage."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    """Return one canonical repository document as strict UTF-8 text."""

    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


class OfficeArchitectureDocumentationContractTest(unittest.TestCase):
    """Keep Office conversion isolation visible without claiming it is shipped."""

    def test_uml_distinguishes_active_adapter_from_planned_runtime(self) -> None:
        """Require an explicit Office adapter isolation flow with truthful maturity."""

        uml = read_text("docs/UML.md").lower()
        for required in (
            "officeconversionadapter",
            "active_pr",
            "sandboxed_office_sidecar",
            "remote_office_service",
            "deterministic_fixture_adapter",
            "planned",
            "api container",
            "outside",
        ):
            with self.subTest(required=required):
                self.assertIn(required, uml)

    def test_data_model_labels_office_qualification_entities_conceptual(self) -> None:
        """Require logical qualification entities without inventing persistence."""

        data_model = read_text("docs/DATA_MODEL.md").lower()
        for required in (
            "conversion_engine",
            "conversion_attempt",
            "conversion_quarantine",
            "adapter_health_snapshot",
            "format_support_record",
            "conversion_audit_event",
            "conceptual",
            "planned",
        ):
            with self.subTest(required=required):
                self.assertIn(required, data_model)

        self.assertIn("does not imply a database table", data_model)

    def test_traceability_keeps_issue_5_and_pr_306_non_shipped(self) -> None:
        """Keep Office qualification linked to its issue and active Draft implementation."""

        traceability = read_text("docs/TRACEABILITY.md").lower()
        for required in (
            "issue #5",
            "#306",
            "active_pr",
            "docs/uml.md",
            "docs/data_model.md",
        ):
            with self.subTest(required=required):
                self.assertIn(required, traceability)


if __name__ == "__main__":
    unittest.main()

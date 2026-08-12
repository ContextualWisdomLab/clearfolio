"""Regression tests for the read-only GitHub Actions workflow registry audit."""

from __future__ import annotations

import unittest

from workflow_registry_audit import (
    AuditIncompleteError,
    WorkflowClass,
    audit_workflow_registry,
)


class WorkflowRegistryAuditTest(unittest.TestCase):
    """Specify fail-closed classification of GitHub Actions registry records."""

    def test_classifies_present_repository_workflow_by_exact_tree_path(self) -> None:
        evidence = audit_workflow_registry(
            pages=[
                {
                    "total_count": 1,
                    "workflows": [
                        {
                            "id": 11,
                            "name": "Historical one-shot-like name",
                            "path": ".github/workflows/one-shot-looking-but-present.yml",
                            "state": "active",
                        }
                    ],
                }
            ],
            tree_paths={".github/workflows/one-shot-looking-but-present.yml"},
            default_branch_sha_before="a" * 40,
            default_branch_sha_after="a" * 40,
            observed_at="2026-08-12T13:00:00Z",
        )

        self.assertEqual(1, evidence["workflow_count"])
        self.assertEqual(WorkflowClass.PRESENT.value, evidence["workflows"][0]["classification"])
        self.assertTrue(evidence["workflows"][0]["file_present"])

    def test_emits_auditable_pagination_receipt(self) -> None:
        evidence = audit_workflow_registry(
            pages=[
                {
                    "total_count": 2,
                    "workflows": [
                        {
                            "id": 19,
                            "name": "CI",
                            "path": ".github/workflows/ci.yml",
                            "state": "active",
                        }
                    ],
                },
                {
                    "total_count": 2,
                    "workflows": [
                        {
                            "id": 20,
                            "name": "Fuzz",
                            "path": ".github/workflows/fuzz.yml",
                            "state": "active",
                        }
                    ],
                },
            ],
            tree_paths={".github/workflows/ci.yml", ".github/workflows/fuzz.yml"},
            default_branch_sha_before="9" * 40,
            default_branch_sha_after="9" * 40,
            observed_at="2026-08-12T13:00:00Z",
        )

        self.assertEqual(
            {"expected_total": 2, "page_count": 2, "page_sizes": [1, 1]},
            evidence["pagination_receipt"],
        )

    def test_classifies_active_repository_path_missing_from_tree_as_orphaned(self) -> None:
        evidence = audit_workflow_registry(
            pages=[
                {
                    "total_count": 1,
                    "workflows": [
                        {
                            "id": 12,
                            "name": "Old repair",
                            "path": ".github/workflows/repair-pr-162.yml",
                            "state": "active",
                        }
                    ],
                }
            ],
            tree_paths={".github/workflows/ci.yml"},
            default_branch_sha_before="b" * 40,
            default_branch_sha_after="b" * 40,
            observed_at="2026-08-12T13:00:00Z",
        )

        record = evidence["workflows"][0]
        self.assertEqual(WorkflowClass.ORPHANED_DELETED.value, record["classification"])
        self.assertFalse(record["file_present"])

    def test_disabled_missing_repository_workflow_is_not_active_orphan(self) -> None:
        evidence = audit_workflow_registry(
            pages=[
                {
                    "total_count": 1,
                    "workflows": [
                        {
                            "id": 13,
                            "name": "Disabled old repair",
                            "path": ".github/workflows/old-repair.yml",
                            "state": "disabled_manually",
                        }
                    ],
                }
            ],
            tree_paths=set(),
            default_branch_sha_before="c" * 40,
            default_branch_sha_after="c" * 40,
            observed_at="2026-08-12T13:00:00Z",
        )

        self.assertEqual(WorkflowClass.DISABLED.value, evidence["workflows"][0]["classification"])
        self.assertEqual(0, evidence["active_orphan_count"])

    def test_dynamic_github_owned_workflow_is_separated_from_repository_paths(self) -> None:
        evidence = audit_workflow_registry(
            pages=[
                {
                    "total_count": 1,
                    "workflows": [
                        {
                            "id": 14,
                            "name": "Copilot",
                            "path": "dynamic/copilot-swe-agent/copilot",
                            "state": "active",
                        }
                    ],
                }
            ],
            tree_paths=set(),
            default_branch_sha_before="d" * 40,
            default_branch_sha_after="d" * 40,
            observed_at="2026-08-12T13:00:00Z",
        )

        record = evidence["workflows"][0]
        self.assertEqual(WorkflowClass.DYNAMIC_GITHUB_OWNED.value, record["classification"])
        self.assertIsNone(record["file_present"])

    def test_case_mismatch_does_not_count_as_exact_file_presence(self) -> None:
        evidence = audit_workflow_registry(
            pages=[
                {
                    "total_count": 1,
                    "workflows": [
                        {
                            "id": 15,
                            "name": "Case mismatch",
                            "path": ".github/workflows/CI.yml",
                            "state": "active",
                        }
                    ],
                }
            ],
            tree_paths={".github/workflows/ci.yml"},
            default_branch_sha_before="e" * 40,
            default_branch_sha_after="e" * 40,
            observed_at="2026-08-12T13:00:00Z",
        )

        record = evidence["workflows"][0]
        self.assertEqual(WorkflowClass.ORPHANED_DELETED.value, record["classification"])
        self.assertFalse(record["file_present"])

    def test_fails_closed_when_registry_pagination_is_truncated(self) -> None:
        with self.assertRaisesRegex(AuditIncompleteError, "workflow registry pagination is incomplete"):
            audit_workflow_registry(
                pages=[
                    {
                        "total_count": 2,
                        "workflows": [
                            {
                                "id": 16,
                                "name": "Only first page record",
                                "path": ".github/workflows/ci.yml",
                                "state": "active",
                            }
                        ],
                    }
                ],
                tree_paths={".github/workflows/ci.yml"},
                default_branch_sha_before="f" * 40,
                default_branch_sha_after="f" * 40,
                observed_at="2026-08-12T13:00:00Z",
            )

    def test_fails_closed_when_registry_stream_is_interrupted(self) -> None:
        def interrupted_pages():
            yield {
                "total_count": 2,
                "workflows": [
                    {
                        "id": 18,
                        "name": "First page before transport failure",
                        "path": ".github/workflows/ci.yml",
                        "state": "active",
                    }
                ],
            }
            raise ConnectionError("simulated transient GitHub API failure")

        with self.assertRaisesRegex(AuditIncompleteError, "workflow registry pages are unavailable"):
            audit_workflow_registry(
                pages=interrupted_pages(),
                tree_paths={".github/workflows/ci.yml"},
                default_branch_sha_before="4" * 40,
                default_branch_sha_after="4" * 40,
                observed_at="2026-08-12T13:00:00Z",
            )

    def test_fails_closed_when_default_branch_moves_during_audit(self) -> None:
        with self.assertRaisesRegex(AuditIncompleteError, "default branch moved during audit"):
            audit_workflow_registry(
                pages=[{"total_count": 0, "workflows": []}],
                tree_paths=set(),
                default_branch_sha_before="1" * 40,
                default_branch_sha_after="2" * 40,
                observed_at="2026-08-12T13:00:00Z",
            )

    def test_rejects_malformed_registry_evidence_and_authority(self) -> None:
        valid_record = {
            "id": 21,
            "name": "CI",
            "path": ".github/workflows/ci.yml",
            "state": "active",
        }
        cases = [
            (
                "malformed page",
                ["not-a-page"],
                "workflow registry page is malformed",
            ),
            (
                "malformed total count",
                [{"total_count": True, "workflows": []}],
                "workflow registry total_count is malformed",
            ),
            (
                "malformed workflows",
                [{"total_count": 0, "workflows": ()}],
                "workflow registry workflows are malformed",
            ),
            (
                "malformed record",
                [{"total_count": 1, "workflows": ["not-a-record"]}],
                "workflow registry record is malformed",
            ),
            (
                "unsupported state",
                [{"total_count": 1, "workflows": [{**valid_record, "state": "queued"}]}],
                "workflow 21 has unsupported state",
            ),
            (
                "unsupported path authority",
                [{"total_count": 1, "workflows": [{**valid_record, "path": "actions/ci.yml"}]}],
                "workflow 21 has unsupported path authority",
            ),
        ]
        for label, pages, error_pattern in cases:
            with self.subTest(label=label):
                with self.assertRaisesRegex(AuditIncompleteError, error_pattern):
                    audit_workflow_registry(
                        pages=pages,
                        tree_paths={".github/workflows/ci.yml"},
                        default_branch_sha_before="5" * 40,
                        default_branch_sha_after="5" * 40,
                        observed_at="2026-08-12T13:00:00Z",
                    )

    def test_rejects_duplicate_registry_records(self) -> None:
        duplicate = {
            "id": 17,
            "name": "Duplicate",
            "path": ".github/workflows/ci.yml",
            "state": "active",
        }
        with self.assertRaisesRegex(AuditIncompleteError, "duplicate workflow id"):
            audit_workflow_registry(
                pages=[{"total_count": 2, "workflows": [duplicate, dict(duplicate)]}],
                tree_paths={".github/workflows/ci.yml"},
                default_branch_sha_before="3" * 40,
                default_branch_sha_after="3" * 40,
                observed_at="2026-08-12T13:00:00Z",
            )


if __name__ == "__main__":
    unittest.main()

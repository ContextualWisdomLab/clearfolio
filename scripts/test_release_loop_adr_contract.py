#!/usr/bin/env python3
"""Protect high-value release and work-conserving automation ADR details."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    """Return one repository text file as original UTF-8 text."""

    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


class ReleaseLoopAdrContractTest(unittest.TestCase):
    """Keep review-driven release and loop semantics explicit and testable."""

    def test_fresh_exit_sweep_is_an_executable_revalidation_procedure(self) -> None:
        """Require each sweep to rebuild authority and queue state from live evidence."""

        adr = read_text("docs/adr/0009-work-conserving-rca-loop.md").lower()
        for required in (
            "each fresh exit sweep",
            "refetch live head and live base",
            "rebuild the executable queue",
            "deferred identities",
            "writer lease",
            "commit statuses",
            "model verdicts",
            "check runs",
            "workflow evidence",
            "invalidate evidence not bound to the current exact identity",
            "safe item appears between sweep one and sweep two",
            "must not terminate",
            "execute any safe item",
            "restart the exit-sweep count after that action",
            "two consecutive fresh sweeps with no safe executable work",
        ):
            with self.subTest(required=required):
                self.assertIn(required, adr)

    def test_release_adr_delegates_to_canonical_complete_acceptance(self) -> None:
        """Prevent a green but incomplete evidence set from satisfying release policy."""

        raw_adr = read_text("docs/adr/0010-release-provenance-fidelity-gate.md")
        self.assertIn("docs/RELEASE_ACCEPTANCE.md", raw_adr)

        adr = raw_adr.lower()
        for required in (
            "zero skips, failures, and errors",
            "zero warnings and deprecations",
            "warning-free public javadocs/doclint",
            "applicable javascript coverage",
            "markdown lint",
            "live required review and security gates",
        ):
            with self.subTest(required=required):
                self.assertIn(required, adr)

        canonical_release = read_text("docs/RELEASE_ACCEPTANCE.md").lower()
        for required in (
            "exact version/tag",
            "immutable dependency lock/resolution state",
            "build artifact digests",
            "sbom/provenance material generated from that source",
            "third-party attribution/license policy evidence",
            "artifact checksums",
            "rollback/recovery follows `docs/migration_rollback.md`",
            "restart/recovery behavior",
        ):
            with self.subTest(contract_owner=required):
                self.assertIn(required, canonical_release)

    def test_scheduler_control_plane_delegates_detail_to_repository_authority(self) -> None:
        """Keep recurring scheduler control compact while preserving repository authority."""

        adr = read_text("docs/adr/0011-thin-scheduler-control-plane.md").lower()
        for required in (
            "thin control plane",
            "repository documents are the detailed authority",
            "must not duplicate the full product specification",
            "read current canonical repository documents before selecting or changing product work",
            "scheduler execution failure",
            "same invocation",
            "double exit sweep",
        ):
            with self.subTest(required=required):
                self.assertIn(required, adr)

        operability = read_text("docs/OPERABILITY.md").lower()
        for required in (
            "scheduled-task execution failure",
            "scheduling/activation",
            "execution failure",
            "prompt-size",
            "duplicated-state",
            "repository authority",
        ):
            with self.subTest(required=required):
                self.assertIn(required, operability)

    def test_scheduler_prewrite_refetch_is_complete_and_fail_closed(self) -> None:
        """Require each write to bind an ordered fresh-state contract before mutation."""

        ordered_contract = re.compile(
            r"before every write.*"
            r"exact (?:source|target) head.*"
            r"live base.*"
            r"target blob/ref.*"
            r"relevant review state.*"
            r"(?:target identity|another writer).*(?:freeze|stop|reject).*(?:rotate|abort)",
            flags=re.DOTALL,
        )
        for relative_path in (
            "docs/adr/0011-thin-scheduler-control-plane.md",
            "docs/OPERABILITY.md",
        ):
            text = " ".join(read_text(relative_path).lower().split())
            with self.subTest(path=relative_path):
                self.assertRegex(text, ordered_contract)
            for required in (
                "formal reviews",
                "unresolved threads",
                "exact-head checks",
                "security gates",
            ):
                with self.subTest(path=relative_path, required=required):
                    self.assertIn(required, text)

    def test_scheduler_adr_ends_with_one_newline(self) -> None:
        """Keep ADR-0011 compliant with the repository Markdown trailing-newline gate."""

        adr = read_text("docs/adr/0011-thin-scheduler-control-plane.md")
        self.assertTrue(adr.endswith("\n"))
        self.assertFalse(adr.endswith("\n\n"))


if __name__ == "__main__":
    unittest.main()

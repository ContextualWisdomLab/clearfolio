#!/usr/bin/env python3
"""Specify deterministic release provenance for the repository-owned OpenAPI contract."""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import openapi_release_provenance as provenance_module
from openapi_release_provenance import (
    CONTRACT_RELATIVE_PATH,
    MAX_CONTRACT_BYTES,
    build_openapi_release_provenance,
    render_openapi_release_provenance,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


class OpenApiReleaseProvenanceTest(unittest.TestCase):
    """Bind the exact public API bytes to one exact protected release identity."""

    def test_builds_deterministic_digest_bound_record(self) -> None:
        """Record exact contract bytes and source revision without environment metadata."""

        contract_bytes = b"openapi: 3.0.3\ninfo:\n  version: 0.1.0\n"
        revision = "0123456789abcdef0123456789abcdef01234567"

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contract_path = root / CONTRACT_RELATIVE_PATH
            contract_path.parent.mkdir(parents=True)
            contract_path.write_bytes(contract_bytes)

            provenance = build_openapi_release_provenance(root, revision)

        self.assertEqual(
            {
                "format": "clearfolio.openapi.release-provenance/v1",
                "contract": CONTRACT_RELATIVE_PATH.as_posix(),
                "sha256": hashlib.sha256(contract_bytes).hexdigest(),
                "sizeBytes": len(contract_bytes),
                "sourceRevision": revision,
            },
            provenance,
        )

    def test_rendering_is_canonical_and_reproducible(self) -> None:
        """Produce one stable JSON representation suitable for signing or packaging."""

        record = {
            "sourceRevision": "0123456789abcdef0123456789abcdef01234567",
            "sizeBytes": 12,
            "sha256": "a" * 64,
            "contract": CONTRACT_RELATIVE_PATH.as_posix(),
            "format": "clearfolio.openapi.release-provenance/v1",
        }

        rendered = render_openapi_release_provenance(record)

        self.assertEqual(json.dumps(record, sort_keys=True, separators=(",", ":")) + "\n", rendered)
        self.assertNotIn(" ", rendered)

    def test_rejects_noncanonical_source_revision(self) -> None:
        """Fail closed instead of binding provenance to ambiguous source identity."""

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contract_path = root / CONTRACT_RELATIVE_PATH
            contract_path.parent.mkdir(parents=True)
            contract_path.write_text("openapi: 3.0.3\n", encoding="utf-8")

            for revision in (
                "",
                "abc",
                "G" * 40,
                "A" * 40,
                "0" * 39,
                "0" * 41,
            ):
                with self.subTest(revision=revision):
                    with self.assertRaisesRegex(ValueError, "source revision must be lowercase 40-hex"):
                        build_openapi_release_provenance(root, revision)

    def test_rejects_missing_symlinked_and_oversized_contracts(self) -> None:
        """Hash only the bounded repository-owned regular file selected by the contract."""

        revision = "f" * 40
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contract_path = root / CONTRACT_RELATIVE_PATH

            with self.assertRaisesRegex(ValueError, "OpenAPI contract must be a regular file"):
                build_openapi_release_provenance(root, revision)

            contract_path.parent.mkdir(parents=True)
            target = root / "external.yaml"
            target.write_text("openapi: 3.0.3\n", encoding="utf-8")
            contract_path.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "OpenAPI contract must be a regular file"):
                build_openapi_release_provenance(root, revision)

            contract_path.unlink()
            contract_path.write_bytes(b"x" * (MAX_CONTRACT_BYTES + 1))
            with self.assertRaisesRegex(ValueError, "OpenAPI contract exceeds release provenance limit"):
                build_openapi_release_provenance(root, revision)

    def test_rejects_contract_identity_change_between_metadata_and_open(self) -> None:
        """Reject a path replacement instead of hashing bytes from a different inode."""

        revision = "e" * 40
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contract_path = root / CONTRACT_RELATIVE_PATH
            contract_path.parent.mkdir(parents=True)
            contract_path.write_text("openapi: 3.0.3\n", encoding="utf-8")
            replacement_path = root / "replacement.yaml"
            replacement_path.write_text("openapi: 3.1.0\n", encoding="utf-8")
            original_open = os.open
            replaced = False

            def replacing_open(path: Path, flags: int) -> int:
                nonlocal replaced
                if not replaced:
                    replacement_path.replace(contract_path)
                    replaced = True
                return original_open(path, flags)

            with patch.object(provenance_module.os, "open", side_effect=replacing_open):
                with self.assertRaisesRegex(
                    ValueError,
                    "OpenAPI contract changed during release provenance read",
                ):
                    build_openapi_release_provenance(root, revision)

    def test_current_repository_contract_is_hashable_without_normalization(self) -> None:
        """Preserve the byte-exact checked-in OpenAPI artifact as the provenance authority."""

        source_revision = "0" * 40
        contract_bytes = (REPOSITORY_ROOT / CONTRACT_RELATIVE_PATH).read_bytes()

        provenance = build_openapi_release_provenance(REPOSITORY_ROOT, source_revision)

        self.assertEqual(hashlib.sha256(contract_bytes).hexdigest(), provenance["sha256"])
        self.assertEqual(len(contract_bytes), provenance["sizeBytes"])
        self.assertEqual(source_revision, provenance["sourceRevision"])


if __name__ == "__main__":
    unittest.main()

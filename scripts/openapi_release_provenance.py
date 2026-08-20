#!/usr/bin/env python3
"""Build byte-exact release provenance for Clearfolio's public OpenAPI contract."""

from __future__ import annotations

import hashlib
import json
import os
import re
import stat
from pathlib import Path
from typing import Mapping


CONTRACT_RELATIVE_PATH = Path("docs/deployment/clearfolio-buyer-connector.openapi.yaml")
"""Repository-relative location of the buyer-facing OpenAPI contract."""

MAX_CONTRACT_BYTES = 1024 * 1024
"""Maximum OpenAPI contract size accepted by release provenance generation."""

_SOURCE_REVISION_PATTERN = re.compile(r"[0-9a-f]{40}")
_READ_CHUNK_BYTES = 64 * 1024


def _read_bounded_regular_file(path: Path) -> bytes:
    """Read one bounded, identity-stable regular file while rejecting symlinks."""

    try:
        link_stat = path.lstat()
    except OSError as exc:
        raise ValueError("OpenAPI contract must be a regular file") from exc
    if not stat.S_ISREG(link_stat.st_mode):
        raise ValueError("OpenAPI contract must be a regular file")
    if link_stat.st_size > MAX_CONTRACT_BYTES:
        raise ValueError("OpenAPI contract exceeds release provenance limit")

    flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ValueError("OpenAPI contract must be a regular file") from exc

    try:
        opened_stat = os.fstat(descriptor)
        if not stat.S_ISREG(opened_stat.st_mode):
            raise ValueError("OpenAPI contract must be a regular file")
        if not os.path.samestat(link_stat, opened_stat):
            raise ValueError("OpenAPI contract changed during release provenance read")
        if opened_stat.st_size > MAX_CONTRACT_BYTES:
            raise ValueError("OpenAPI contract exceeds release provenance limit")

        chunks: list[bytes] = []
        remaining = MAX_CONTRACT_BYTES + 1
        while remaining > 0:
            chunk = os.read(descriptor, min(_READ_CHUNK_BYTES, remaining))
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        contract_bytes = b"".join(chunks)
        if len(contract_bytes) > MAX_CONTRACT_BYTES:
            raise ValueError("OpenAPI contract exceeds release provenance limit")
        return contract_bytes
    finally:
        os.close(descriptor)


def build_openapi_release_provenance(repository_root: Path, source_revision: str) -> dict[str, object]:
    """Bind exact OpenAPI bytes to one canonical Git source revision.

    Args:
        repository_root: Root directory containing the repository checkout.
        source_revision: Lowercase 40-hex Git commit identifier for the release source.

    Returns:
        Canonical release-provenance fields suitable for deterministic rendering.

    Raises:
        ValueError: If source identity or contract-file authority is invalid.
    """

    if not isinstance(source_revision, str) or _SOURCE_REVISION_PATTERN.fullmatch(source_revision) is None:
        raise ValueError("source revision must be lowercase 40-hex")

    root = Path(repository_root)
    contract_bytes = _read_bounded_regular_file(root / CONTRACT_RELATIVE_PATH)
    return {
        "format": "clearfolio.openapi.release-provenance/v1",
        "contract": CONTRACT_RELATIVE_PATH.as_posix(),
        "sha256": hashlib.sha256(contract_bytes).hexdigest(),
        "sizeBytes": len(contract_bytes),
        "sourceRevision": source_revision,
    }


def render_openapi_release_provenance(record: Mapping[str, object]) -> str:
    """Render a provenance record as stable compact JSON with one trailing newline."""

    return json.dumps(record, sort_keys=True, separators=(",", ":")) + "\n"

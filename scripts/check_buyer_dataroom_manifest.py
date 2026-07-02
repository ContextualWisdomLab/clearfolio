#!/usr/bin/env python3
"""Check the Clearfolio buyer data-room manifest for missing evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from urllib.parse import urlparse


VALID_STATUSES = {"ready", "partial", "external"}


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def check_manifest(root: Path, manifest: dict) -> dict:
    errors: list[str] = []
    artifacts = manifest.get("artifacts", [])
    gates = manifest.get("readinessGates", [])

    if manifest.get("manifestVersion") != 1:
        errors.append("manifestVersion must be 1")
    if not str(manifest.get("packageName", "")).strip():
        errors.append("packageName is required")
    if not isinstance(artifacts, list):
        errors.append("artifacts must be a list")
        artifacts = []
    if not isinstance(gates, list):
        errors.append("readinessGates must be a list")
        gates = []

    artifact_ids: set[str] = set()
    for artifact in artifacts:
        artifact_id = str(artifact.get("id", "")).strip()
        if not artifact_id:
            errors.append("artifact id is required")
            continue
        if artifact_id in artifact_ids:
            errors.append(f"duplicate artifact id: {artifact_id}")
        artifact_ids.add(artifact_id)
        status = str(artifact.get("status", "")).strip()
        if status not in VALID_STATUSES:
            errors.append(f"artifact {artifact_id} has invalid status: {status}")
        path = str(artifact.get("path", "")).strip()
        url = str(artifact.get("url", "")).strip()
        if path:
            if not (root / path).is_file():
                errors.append(f"artifact {artifact_id} path does not exist: {path}")
        elif url:
            parsed = urlparse(url)
            if parsed.scheme not in {"http", "https"} or not parsed.netloc:
                errors.append(f"artifact {artifact_id} has invalid url: {url}")
        else:
            errors.append(f"artifact {artifact_id} must define path or url")

    for gate in gates:
        gate_id = str(gate.get("id", "")).strip()
        if not gate_id:
            errors.append("gate id is required")
            continue
        status = str(gate.get("status", "")).strip()
        if status not in VALID_STATUSES:
            errors.append(f"gate {gate_id} has invalid status: {status}")
        evidence = gate.get("evidence", [])
        if not evidence:
            errors.append(f"gate {gate_id} must reference evidence")
            continue
        for artifact_id in evidence:
            if artifact_id not in artifact_ids:
                errors.append(f"gate {gate_id} references unknown artifact: {artifact_id}")

    return {
        "artifactCount": len(artifacts),
        "gateCount": len(gates),
        "errors": errors,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args(argv)

    result = check_manifest(args.root, load_json(args.manifest))
    output = json.dumps(result, indent=2, sort_keys=True)
    if args.summary:
        args.summary.write_text(output + "\n", encoding="utf-8")
    print(output)
    return 0 if not result["errors"] else 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

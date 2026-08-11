"""Detect removals from Clearfolio's checked-in OpenAPI v1 compatibility baseline."""

from __future__ import annotations

from collections.abc import Mapping
import re
from typing import Any


_HTTP_METHODS = {"delete", "get", "head", "options", "patch", "post", "put", "trace"}
_RESPONSE_KEY = re.compile(r'^        ["\']?([1-5][0-9][0-9]|default)["\']?:\s*$')
_MAX_SPEC_BYTES = 1_000_000


def collect_operations(source: str) -> dict[str, dict[str, Any]]:
    """Collect stable client-visible operation identity from bounded OpenAPI YAML text.

    The repository intentionally avoids adding a YAML dependency for this narrow
    gate. Only the indentation levels owned by the OpenAPI ``paths`` object are
    interpreted: path keys, HTTP method keys, ``operationId``, and response
    status keys. Nested schemas and path-level metadata are ignored.
    """
    if len(source.encode("utf-8")) > _MAX_SPEC_BYTES:
        raise ValueError("OpenAPI source exceeds compatibility-gate size limit")

    operations: dict[str, dict[str, Any]] = {}
    in_paths = False
    current_path: str | None = None
    current_operation: dict[str, Any] | None = None
    in_responses = False

    for raw_line in source.splitlines():
        if raw_line == "paths:":
            in_paths = True
            current_path = None
            current_operation = None
            in_responses = False
            continue
        if not in_paths:
            continue
        if raw_line and not raw_line.startswith(" "):
            break

        if raw_line.startswith("  /") and not raw_line.startswith("    ") and raw_line.endswith(":"):
            current_path = raw_line.strip()[:-1]
            current_operation = None
            in_responses = False
            continue

        if current_path is None:
            continue

        if raw_line.startswith("    ") and not raw_line.startswith("      ") and raw_line.endswith(":"):
            candidate_method = raw_line.strip()[:-1].lower()
            in_responses = False
            if candidate_method in _HTTP_METHODS:
                key = f"{candidate_method.upper()} {current_path}"
                current_operation = {"operationId": None, "responses": []}
                operations[key] = current_operation
            else:
                current_operation = None
            continue

        if current_operation is None:
            continue

        if raw_line.startswith("      operationId:"):
            value = raw_line.split(":", 1)[1].strip()
            current_operation["operationId"] = _unquote(value)
            continue

        if raw_line == "      responses:":
            in_responses = True
            continue

        if in_responses:
            if raw_line.startswith("      ") and not raw_line.startswith("        ") and raw_line.strip():
                in_responses = False
                continue
            match = _RESPONSE_KEY.match(raw_line)
            if match:
                current_operation["responses"].append(match.group(1))

    for operation in operations.values():
        operation["responses"] = sorted(set(operation["responses"]))
    return operations


def find_breaking_changes(
        baseline: Mapping[str, Mapping[str, Any]],
        candidate: Mapping[str, Mapping[str, Any]]) -> list[str]:
    """Return deterministic client-visible removals from ``baseline`` to ``candidate``."""
    findings: list[str] = []
    for operation_key in sorted(baseline):
        expected = baseline[operation_key]
        actual = candidate.get(operation_key)
        if actual is None:
            findings.append(f"{operation_key}: operation was removed")
            continue

        expected_operation_id = expected.get("operationId")
        actual_operation_id = actual.get("operationId")
        if actual_operation_id != expected_operation_id:
            findings.append(
                f"{operation_key}: operationId changed from "
                f"{expected_operation_id} to {actual_operation_id}"
            )

        expected_responses = {str(status) for status in expected.get("responses", [])}
        actual_responses = {str(status) for status in actual.get("responses", [])}
        for removed_status in sorted(expected_responses - actual_responses):
            findings.append(f"{operation_key}: response {removed_status} was removed")

    return findings


def _unquote(value: str) -> str:
    """Remove matching single or double quotes from one simple YAML scalar."""
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value

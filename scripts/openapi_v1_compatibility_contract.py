"""Detect removals from Clearfolio's checked-in OpenAPI v1 compatibility baseline."""

from __future__ import annotations

from collections.abc import Mapping
import re
from typing import Any


_HTTP_METHODS = {"delete", "get", "head", "options", "patch", "post", "put", "trace"}
_RESPONSE_KEY = re.compile(r"(?:[1-5][0-9]{2}|default)\Z")
_MAX_SPEC_BYTES = 1_000_000


def collect_operations(source: str) -> dict[str, dict[str, Any]]:
    """Collect client-visible operation identity from bounded OpenAPI YAML text.

    This dependency-free scanner follows relative YAML mapping indentation under
    the top-level ``paths`` mapping instead of requiring one exact whitespace
    layout. It accepts simple quoted keys, inline comments, and mapping anchors
    while extracting only direct path, HTTP method, ``operationId``, and response
    mappings. Unrelated nested schema content is ignored.
    """
    if len(source.encode("utf-8")) > _MAX_SPEC_BYTES:
        raise ValueError("OpenAPI source exceeds compatibility-gate size limit")

    operations: dict[str, dict[str, Any]] = {}
    paths_indent: int | None = None
    current_path: str | None = None
    path_indent: int | None = None
    path_child_indent: int | None = None
    current_operation: dict[str, Any] | None = None
    method_indent: int | None = None
    method_child_indent: int | None = None
    responses_indent: int | None = None
    response_child_indent: int | None = None

    for raw_line in source.splitlines():
        entry = _mapping_entry(raw_line)
        if entry is None:
            continue
        indent, key, value = entry

        if paths_indent is None:
            if indent == 0 and key == "paths":
                paths_indent = indent
            continue
        if indent <= paths_indent:
            break

        if path_indent is not None and indent <= path_indent:
            current_path = None
            path_indent = None
            path_child_indent = None
            current_operation = None
            method_indent = None
            method_child_indent = None
            responses_indent = None
            response_child_indent = None
        elif method_indent is not None and indent <= method_indent:
            current_operation = None
            method_indent = None
            method_child_indent = None
            responses_indent = None
            response_child_indent = None
        elif responses_indent is not None and indent <= responses_indent:
            responses_indent = None
            response_child_indent = None

        if current_path is None:
            if key.startswith("/"):
                current_path = key
                path_indent = indent
                path_child_indent = None
            continue

        if current_operation is None:
            if path_child_indent is None and path_indent is not None and indent > path_indent:
                path_child_indent = indent
            if indent == path_child_indent and key.lower() in _HTTP_METHODS:
                method = key.lower()
                current_operation = {"operationId": None, "responses": []}
                operations[f"{method.upper()} {current_path}"] = current_operation
                method_indent = indent
                method_child_indent = None
            continue

        if responses_indent is not None and indent > responses_indent:
            if response_child_indent is None:
                response_child_indent = indent
            if indent == response_child_indent:
                response_key = _unquote(key)
                if _RESPONSE_KEY.fullmatch(response_key):
                    current_operation["responses"].append(response_key)
            continue

        if method_child_indent is None and method_indent is not None and indent > method_indent:
            method_child_indent = indent
        if indent != method_child_indent:
            continue

        if key == "operationId":
            current_operation["operationId"] = _unquote(value)
        elif key == "responses":
            responses_indent = indent
            response_child_indent = None

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


def _mapping_entry(raw_line: str) -> tuple[int, str, str] | None:
    """Return a simple YAML mapping entry and its leading-space indentation."""
    prefix = raw_line[: len(raw_line) - len(raw_line.lstrip(" \t"))]
    if "\t" in prefix:
        raise ValueError("OpenAPI compatibility source must use spaces for indentation")

    stripped = raw_line.strip()
    if not stripped or stripped.startswith("#") or stripped.startswith("-") or ":" not in stripped:
        return None

    key, value = stripped.split(":", 1)
    key = _unquote(key.strip())
    value = value.split(" #", 1)[0].strip()
    return len(prefix), key, value


def _unquote(value: str) -> str:
    """Remove matching single or double quotes from one simple YAML scalar."""
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value

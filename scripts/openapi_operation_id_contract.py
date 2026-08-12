#!/usr/bin/env python3
"""Inspect the buyer OpenAPI path table for stable, unique operation identifiers.

This checker intentionally uses only the Python standard library so the repository's
buyer-readiness gate does not need a second YAML runtime. It recognizes the narrowly
formatted top-level ``paths`` table owned by this repository and treats only standard
HTTP method keys as operations. It does not attempt to be a general-purpose YAML parser.
"""

from __future__ import annotations

from dataclasses import dataclass


HTTP_METHODS = frozenset({"get", "put", "post", "delete", "options", "head", "patch", "trace"})


@dataclass(frozen=True)
class ContractViolation:
    """One deterministic operationId contract violation."""

    code: str
    detail: str


@dataclass(frozen=True)
class InspectionResult:
    """Operations found in the path table and any contract violations."""

    operations: list[tuple[str, str, str | None]]
    violations: list[ContractViolation]


def _indent_width(line: str) -> int:
    """Return leading-space indentation and reject tab-indented structure."""

    prefix = line[: len(line) - len(line.lstrip(" \t"))]
    if "\t" in prefix:
        raise ValueError("OpenAPI contract must use spaces for structural indentation")
    return len(prefix)


def _yaml_scalar(value: str) -> str:
    """Return the simple scalar form used by repository-owned operationId values.

    YAML plain scalars may contain ``#`` as data when it is not preceded by
    separation whitespace. A separated ``#`` starts a comment and is therefore
    excluded from the operation identifier. Quoted scalar support remains narrow:
    the complete trimmed value must be enclosed by one matching quote pair.
    """

    value = value.strip()
    if not value:
        return ""

    if value[0] in {"'", '"'}:
        if len(value) < 2 or value[-1] != value[0]:
            raise ValueError("unsupported quoted operationId scalar")
        return value[1:-1]

    for index, character in enumerate(value):
        if character == "#" and (index == 0 or value[index - 1].isspace()):
            return value[:index].rstrip()
    return value


def inspect_operation_ids(contract: str) -> InspectionResult:
    """Inspect standard HTTP methods under the top-level OpenAPI ``paths`` mapping.

    The repository-owned contract keeps path keys at two spaces, method keys at four
    spaces, and direct method properties at six spaces. Path-level metadata such as
    ``parameters`` or ``$ref`` is ignored because it is not an HTTP operation, and
    nested extension metadata cannot satisfy the direct ``operationId`` contract.
    """

    operations: list[tuple[str, str, str | None]] = []
    violations: list[ContractViolation] = []
    first_use: dict[str, tuple[str, str]] = {}

    in_paths = False
    current_path: str | None = None
    current_method: str | None = None
    current_operation_id: str | None = None

    def finish_operation() -> None:
        nonlocal current_method, current_operation_id
        if current_path is None or current_method is None:
            return

        method = current_method.upper()
        operation_id = current_operation_id
        operations.append((method, current_path, operation_id))

        if not operation_id:
            violations.append(ContractViolation(
                code="missing_operation_id",
                detail=f"{method} {current_path} does not declare operationId",
            ))
        else:
            previous = first_use.get(operation_id)
            if previous is None:
                first_use[operation_id] = (method, current_path)
            else:
                previous_method, previous_path = previous
                violations.append(ContractViolation(
                    code="duplicate_operation_id",
                    detail=(
                        f"operationId '{operation_id}' is used by "
                        f"{previous_method} {previous_path} and {method} {current_path}"
                    ),
                ))

        current_method = None
        current_operation_id = None

    for raw_line in contract.splitlines():
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#"):
            continue

        indent = _indent_width(raw_line)

        if not in_paths:
            if indent == 0 and stripped == "paths:":
                in_paths = True
            continue

        if indent == 0:
            finish_operation()
            break

        if indent == 2 and stripped.endswith(":"):
            finish_operation()
            key = stripped[:-1].strip()
            if key.startswith("/"):
                current_path = key
            else:
                current_path = None
            continue

        if current_path is None:
            continue

        if indent == 4 and stripped.endswith(":"):
            finish_operation()
            key = stripped[:-1].strip().lower()
            if key in HTTP_METHODS:
                current_method = key
            continue

        if current_method is not None and indent == 6 and stripped.startswith("operationId:"):
            _, value = stripped.split(":", 1)
            candidate = _yaml_scalar(value)
            current_operation_id = candidate or None

    finish_operation()
    return InspectionResult(operations=operations, violations=violations)

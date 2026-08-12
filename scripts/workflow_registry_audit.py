"""Classify GitHub Actions workflow registry evidence against one stable default-branch tree.

The module is deliberately read-only and side-effect free. Callers gather all GitHub API
pages and the exact default-branch tree, then pass that evidence here for fail-closed
classification.
"""

from __future__ import annotations

from enum import Enum
import re
from typing import Any, Final, Iterable, Mapping

_SHA_PATTERN: Final[re.Pattern[str]] = re.compile(r"^[0-9a-f]{40}$")
_REPOSITORY_WORKFLOW_PREFIX: Final[str] = ".github/workflows/"
_DYNAMIC_WORKFLOW_PREFIX: Final[str] = "dynamic/"


class AuditIncompleteError(RuntimeError):
    """Report incomplete, contradictory, or malformed workflow-registry evidence."""


class WorkflowClass(str, Enum):
    """Describe how one workflow registry identity relates to the protected tree."""

    PRESENT = "present"
    ORPHANED_DELETED = "orphaned_deleted"
    DISABLED = "disabled"
    DYNAMIC_GITHUB_OWNED = "dynamic_github_owned"


def _require_sha(value: object, label: str) -> str:
    """Return one canonical Git commit SHA or fail closed."""
    if not isinstance(value, str) or _SHA_PATTERN.fullmatch(value) is None:
        raise AuditIncompleteError(f"{label} must be a canonical 40-character SHA")
    return value


def _require_text(value: object, label: str) -> str:
    """Return one non-empty string field or fail closed."""
    if not isinstance(value, str) or not value:
        raise AuditIncompleteError(f"{label} must be a non-empty string")
    return value


def _normalize_tree_paths(tree_paths: Iterable[str]) -> set[str]:
    """Materialize exact case-sensitive protected-tree paths after validating them."""
    try:
        paths = set(tree_paths)
    except (TypeError, ValueError) as error:
        raise AuditIncompleteError("tree paths must be a finite iterable of strings") from error
    if any(not isinstance(path, str) or not path for path in paths):
        raise AuditIncompleteError("tree paths must contain only non-empty strings")
    return paths


def _flatten_pages(pages: Iterable[Mapping[str, Any]]) -> tuple[int, list[Mapping[str, Any]]]:
    """Validate complete GitHub pagination evidence and return its registry records."""
    try:
        page_list = list(pages)
    except TypeError as error:
        raise AuditIncompleteError("workflow registry pages are unavailable") from error
    if not page_list:
        raise AuditIncompleteError("workflow registry pages are unavailable")

    expected_total: int | None = None
    records: list[Mapping[str, Any]] = []
    for page in page_list:
        if not isinstance(page, Mapping):
            raise AuditIncompleteError("workflow registry page is malformed")
        total_count = page.get("total_count")
        workflows = page.get("workflows")
        if not isinstance(total_count, int) or isinstance(total_count, bool) or total_count < 0:
            raise AuditIncompleteError("workflow registry total_count is malformed")
        if not isinstance(workflows, list):
            raise AuditIncompleteError("workflow registry workflows are malformed")
        if expected_total is None:
            expected_total = total_count
        elif total_count != expected_total:
            raise AuditIncompleteError("workflow registry pagination total_count changed")
        if any(not isinstance(record, Mapping) for record in workflows):
            raise AuditIncompleteError("workflow registry record is malformed")
        records.extend(workflows)

    if expected_total is None or len(records) != expected_total:
        raise AuditIncompleteError("workflow registry pagination is incomplete")
    return expected_total, records


def _classify_record(record: Mapping[str, Any], tree_paths: set[str]) -> dict[str, object]:
    """Validate and classify one workflow identity using exact protected-tree membership."""
    workflow_id = record.get("id")
    if not isinstance(workflow_id, int) or isinstance(workflow_id, bool) or workflow_id <= 0:
        raise AuditIncompleteError("workflow id must be a positive integer")
    name = _require_text(record.get("name"), "workflow name")
    path = _require_text(record.get("path"), "workflow path")
    state = _require_text(record.get("state"), "workflow state")

    if state != "active" and not state.startswith("disabled_"):
        raise AuditIncompleteError(f"workflow {workflow_id} has unsupported state")

    if path.startswith(_DYNAMIC_WORKFLOW_PREFIX):
        if state == "active":
            classification = WorkflowClass.DYNAMIC_GITHUB_OWNED
        else:
            classification = WorkflowClass.DISABLED
        file_present: bool | None = None
    elif path.startswith(_REPOSITORY_WORKFLOW_PREFIX):
        file_present = path in tree_paths
        if state != "active":
            classification = WorkflowClass.DISABLED
        elif file_present:
            classification = WorkflowClass.PRESENT
        else:
            classification = WorkflowClass.ORPHANED_DELETED
    else:
        raise AuditIncompleteError(f"workflow {workflow_id} has unsupported path authority")

    return {
        "id": workflow_id,
        "name": name,
        "path": path,
        "state": state,
        "classification": classification.value,
        "file_present": file_present,
    }


def audit_workflow_registry(
    *,
    pages: Iterable[Mapping[str, Any]],
    tree_paths: Iterable[str],
    default_branch_sha_before: str,
    default_branch_sha_after: str,
    observed_at: str,
) -> dict[str, object]:
    """Build fail-closed workflow-registry evidence for one stable protected revision.

    Args:
        pages: Every GitHub Actions workflow-list page from one complete observation.
        tree_paths: Exact case-sensitive paths from the protected default-branch tree.
        default_branch_sha_before: Default-branch SHA resolved before evidence collection.
        default_branch_sha_after: Default-branch SHA resolved after evidence collection.
        observed_at: Caller-owned observation timestamp recorded with the evidence.

    Returns:
        A JSON-serializable evidence mapping containing the stable revision and each
        validated workflow identity's classification.

    Raises:
        AuditIncompleteError: If pagination, branch identity, registry records, or tree
            evidence is incomplete, contradictory, or malformed.
    """
    before = _require_sha(default_branch_sha_before, "default branch SHA before audit")
    after = _require_sha(default_branch_sha_after, "default branch SHA after audit")
    if before != after:
        raise AuditIncompleteError("default branch moved during audit")
    observation = _require_text(observed_at, "observation time")
    exact_tree_paths = _normalize_tree_paths(tree_paths)
    workflow_count, raw_records = _flatten_pages(pages)

    seen_ids: set[int] = set()
    classified: list[dict[str, object]] = []
    for raw_record in raw_records:
        record = _classify_record(raw_record, exact_tree_paths)
        workflow_id = record["id"]
        if workflow_id in seen_ids:
            raise AuditIncompleteError(f"duplicate workflow id {workflow_id}")
        seen_ids.add(workflow_id)
        classified.append(record)

    return {
        "default_branch_sha": before,
        "observed_at": observation,
        "workflow_count": workflow_count,
        "active_orphan_count": sum(
            record["classification"] == WorkflowClass.ORPHANED_DELETED.value
            for record in classified
        ),
        "workflows": classified,
    }

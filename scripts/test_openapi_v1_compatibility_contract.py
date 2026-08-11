"""Regression tests for the versioned OpenAPI compatibility gate."""

from __future__ import annotations

import json
from pathlib import Path
import sys


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = REPOSITORY_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

from openapi_v1_compatibility_contract import (  # noqa: E402
    collect_operations,
    find_breaking_changes,
)


CURRENT_SPEC = REPOSITORY_ROOT / "docs" / "deployment" / "clearfolio-buyer-connector.openapi.yaml"
BASELINE = SCRIPTS_DIR / "openapi_v1_compatibility_baseline.json"


def test_collects_operation_identity_and_response_contract() -> None:
    """Extract path/method identity, operationId, and status codes from OpenAPI YAML."""
    source = """\
openapi: 3.0.3
paths:
  /api/v1/widgets/{widgetId}:
    parameters:
      - name: widgetId
    get:
      operationId: getWidget
      responses:
        \"200\":
          description: OK
        \"404\":
          description: Missing
"""

    assert collect_operations(source) == {
        "GET /api/v1/widgets/{widgetId}": {
            "operationId": "getWidget",
            "responses": ["200", "404"],
        }
    }


def test_detects_removed_operation_operation_id_change_and_removed_response() -> None:
    """Reject client-visible v1 contract removals while allowing additive changes."""
    baseline = {
        "GET /api/v1/widgets/{widgetId}": {
            "operationId": "getWidget",
            "responses": ["200", "404"],
        },
        "POST /api/v1/widgets": {
            "operationId": "createWidget",
            "responses": ["201", "400"],
        },
    }
    candidate = {
        "GET /api/v1/widgets/{widgetId}": {
            "operationId": "fetchWidget",
            "responses": ["200", "409"],
        },
        "DELETE /api/v1/widgets/{widgetId}": {
            "operationId": "deleteWidget",
            "responses": ["204"],
        },
    }

    assert find_breaking_changes(baseline, candidate) == [
        "GET /api/v1/widgets/{widgetId}: operationId changed from getWidget to fetchWidget",
        "GET /api/v1/widgets/{widgetId}: response 404 was removed",
        "POST /api/v1/widgets: operation was removed",
    ]


def test_additive_operations_and_responses_are_compatible() -> None:
    """Do not block an additive v1 operation or response status."""
    baseline = {
        "GET /api/v1/widgets/{widgetId}": {
            "operationId": "getWidget",
            "responses": ["200"],
        }
    }
    candidate = {
        "GET /api/v1/widgets/{widgetId}": {
            "operationId": "getWidget",
            "responses": ["200", "404"],
        },
        "POST /api/v1/widgets": {
            "operationId": "createWidget",
            "responses": ["201"],
        },
    }

    assert find_breaking_changes(baseline, candidate) == []


def test_checked_in_v1_baseline_accepts_current_buyer_contract() -> None:
    """The approved v1 baseline must be a subset of the repository-owned current spec."""
    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    candidate = collect_operations(CURRENT_SPEC.read_text(encoding="utf-8"))

    assert baseline
    assert find_breaking_changes(baseline, candidate) == []

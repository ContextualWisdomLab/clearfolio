"""Exercise the combined versioned API and tagged-release contract foundation."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = REPOSITORY_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

from openapi_operation_id_contract import inspect_operation_ids  # noqa: E402
from openapi_release_provenance import build_openapi_release_provenance  # noqa: E402
from openapi_v1_compatibility_contract import (  # noqa: E402
    collect_operations,
    find_breaking_changes,
)


OPENAPI_PATH = REPOSITORY_ROOT / "docs/deployment/clearfolio-buyer-connector.openapi.yaml"
BASELINE_PATH = SCRIPTS_DIR / "openapi_v1_compatibility_baseline.json"


def test_current_openapi_identity_compatibility_and_provenance_share_exact_bytes() -> None:
    """Bind generated-client identity, v1 compatibility, and release digest to one artifact."""

    contract_bytes = OPENAPI_PATH.read_bytes()
    contract_text = contract_bytes.decode("utf-8")

    identity_result = inspect_operation_ids(contract_text)
    compatibility_operations = collect_operations(contract_text)
    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    provenance = build_openapi_release_provenance(REPOSITORY_ROOT, "0" * 40)

    assert identity_result.operations
    assert identity_result.violations == []
    assert find_breaking_changes(baseline, compatibility_operations) == []
    assert {
        f"{method} {path}": operation_id
        for method, path, operation_id in identity_result.operations
    } == {
        operation_key: operation["operationId"]
        for operation_key, operation in compatibility_operations.items()
    }
    assert provenance["sha256"] == hashlib.sha256(contract_bytes).hexdigest()
    assert provenance["sizeBytes"] == len(contract_bytes)
    assert provenance["sourceRevision"] == "0" * 40

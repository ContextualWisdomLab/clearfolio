#!/usr/bin/env python3
"""Contract tests for stable, unique buyer OpenAPI operation identifiers."""

from __future__ import annotations

import unittest
from pathlib import Path

from scripts.openapi_operation_id_contract import ContractViolation, inspect_operation_ids


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
OPENAPI_PATH = REPOSITORY_ROOT / "docs/deployment/clearfolio-buyer-connector.openapi.yaml"


class OpenApiOperationIdContractTest(unittest.TestCase):
    """Keep every shipped HTTP operation addressable by one stable operationId."""

    def test_current_buyer_contract_has_unique_operation_ids(self) -> None:
        """The repository-owned buyer contract must contain no missing or duplicate IDs."""

        contract = OPENAPI_PATH.read_text(encoding="utf-8")
        result = inspect_operation_ids(contract)

        self.assertGreater(len(result.operations), 0)
        self.assertEqual([], result.violations)

    def test_duplicate_operation_id_is_rejected(self) -> None:
        """Two HTTP operations must never share the same generated-client identity."""

        contract = """openapi: 3.0.3
paths:
  /api/v1/jobs:
    get:
      operationId: readJob
  /api/v1/items:
    post:
      operationId: readJob
components: {}
"""

        result = inspect_operation_ids(contract)

        self.assertIn(
            ContractViolation(
                code="duplicate_operation_id",
                detail="operationId 'readJob' is used by GET /api/v1/jobs and POST /api/v1/items",
            ),
            result.violations,
        )

    def test_missing_operation_id_is_rejected(self) -> None:
        """Every path-level HTTP method must declare an explicit operationId."""

        contract = """openapi: 3.0.3
paths:
  /api/v1/jobs:
    parameters: []
    get:
      summary: Read a job
components: {}
"""

        result = inspect_operation_ids(contract)

        self.assertIn(
            ContractViolation(
                code="missing_operation_id",
                detail="GET /api/v1/jobs does not declare operationId",
            ),
            result.violations,
        )

    def test_non_http_path_keys_do_not_create_operations(self) -> None:
        """OpenAPI path-level metadata is not mistaken for an HTTP operation."""

        contract = """openapi: 3.0.3
paths:
  /api/v1/jobs/{jobId}:
    parameters: []
    get:
      operationId: readJob
components: {}
"""

        result = inspect_operation_ids(contract)

        self.assertEqual([("GET", "/api/v1/jobs/{jobId}", "readJob")], result.operations)
        self.assertEqual([], result.violations)


if __name__ == "__main__":
    unittest.main()

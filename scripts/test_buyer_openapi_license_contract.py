#!/usr/bin/env python3
"""Keep buyer OpenAPI acquisition metadata aligned with repository authority."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
OPENAPI_PATH = REPOSITORY_ROOT / "docs/deployment/clearfolio-buyer-connector.openapi.yaml"
LICENSE_PATH = REPOSITORY_ROOT / "LICENSE"


class BuyerOpenApiLicenseContractTest(unittest.TestCase):
    """Prevent acquisition-facing API metadata from contradicting product authority."""

    def test_openapi_does_not_claim_proprietary_repository_licensing(self) -> None:
        """Reject the superseded proprietary-only buyer-diligence claim."""

        repository_license = LICENSE_PATH.read_text(encoding="utf-8")
        openapi = OPENAPI_PATH.read_text(encoding="utf-8")

        self.assertTrue(repository_license.startswith("Apache License\nVersion 2.0"))
        self.assertNotIn("Proprietary - buyer diligence use only", openapi)

    def test_openapi_examples_do_not_encode_buyer_demo_identity(self) -> None:
        """Keep machine-readable integration examples independent of demo authority."""

        openapi = OPENAPI_PATH.read_text(encoding="utf-8")

        self.assertNotIn("buyer-demo", openapi)
        self.assertNotIn("buyer-demo-operator", openapi)
        self.assertIn("example: tenant-example", openapi)
        self.assertIn("example: operator-example", openapi)


if __name__ == "__main__":
    unittest.main()

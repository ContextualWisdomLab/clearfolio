#!/usr/bin/env python3
"""Keep the buyer OpenAPI license metadata aligned with repository authority."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
OPENAPI_PATH = REPOSITORY_ROOT / "docs/deployment/clearfolio-buyer-connector.openapi.yaml"
LICENSE_PATH = REPOSITORY_ROOT / "LICENSE"


class BuyerOpenApiLicenseContractTest(unittest.TestCase):
    """Prevent acquisition-facing API metadata from contradicting the source license."""

    def test_openapi_uses_repository_apache_license(self) -> None:
        """Require the buyer connector seed to publish Apache-2.0 license metadata."""

        repository_license = LICENSE_PATH.read_text(encoding="utf-8")
        openapi = OPENAPI_PATH.read_text(encoding="utf-8")

        self.assertTrue(repository_license.startswith("Apache License\nVersion 2.0"))
        self.assertIn("    name: Apache-2.0", openapi)
        self.assertIn("https://www.apache.org/licenses/LICENSE-2.0.html", openapi)
        self.assertNotIn("Proprietary - buyer diligence use only", openapi)


if __name__ == "__main__":
    unittest.main()

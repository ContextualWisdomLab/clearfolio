"""Protect Clearfolio runtime signing secrets from direct environment binding."""

from pathlib import Path
import unittest


APPLICATION = Path("src/main/resources/application.yml")
BUYER_DEMO = Path("src/main/resources/application-buyer-demo.yml")

DIRECT_RUNTIME_SECRET_ENV_VARS = (
    "CLEARFOLIO_ARTIFACT_TOKEN_SECRET",
    "CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET",
)


class RuntimeSecretSourceContractTest(unittest.TestCase):
    """Verify configtree authority through standard unittest discovery."""

    def test_buyer_demo_does_not_bind_runtime_signing_secrets_from_environment(self) -> None:
        """Keep signing-key authority out of Spring environment placeholders."""

        source = BUYER_DEMO.read_text(encoding="utf-8")

        for environment_variable in DIRECT_RUNTIME_SECRET_ENV_VARS:
            self.assertNotIn(
                environment_variable,
                source,
                "runtime signing secret must come from the configured KV/configtree, "
                f"not direct environment binding: {environment_variable}",
            )

    def test_default_config_keeps_kv_configtree_bootstrap_for_runtime_secrets(self) -> None:
        """Require the shared configtree bootstrap used by secret property files."""

        source = APPLICATION.read_text(encoding="utf-8")

        self.assertIn(
            'import: "optional:configtree:${CLEARFOLIO_SECRET_CONFIG_DIR:/run/secrets/clearfolio/}"',
            source,
            "runtime secret configtree bootstrap is missing",
        )
        self.assertIn("clearfolio.artifact-token.secret", source)
        self.assertIn("clearfolio.tenant-claims.hmac-secret", source)


if __name__ == "__main__":
    unittest.main()

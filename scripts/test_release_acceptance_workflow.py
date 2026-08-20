from pathlib import Path


WORKFLOW = Path(".github/workflows/release-acceptance.yml")


def test_release_acceptance_is_tag_scoped_and_exact_revision_bound() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "tags: [ 'v*' ]" in text or "tags: ['v*']" in text
    assert "permissions:\n  contents: read" in text
    assert "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1" in text
    assert "persist-credentials: false" in text
    assert "ref: ${{ github.sha }}" in text
    assert 'test "$(git rev-parse HEAD)" = "$EXPECTED_SHA"' in text


def test_release_acceptance_runs_hash_locked_api_contracts() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "actions/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97" in text
    assert "--require-hashes -r requirements-test.txt" in text
    assert "python -m pytest -q scripts" in text


def test_release_acceptance_verifies_build_sbom_license_and_tag_version() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "mvn -B --no-transfer-progress verify" in text
    assert "org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom" in text
    assert "scripts/check_sbom_license_policy.py" in text
    assert "--require-no-review" in text
    assert "help:evaluate -Dexpression=project.version" in text
    assert "GITHUB_REF_NAME" in text


def test_release_acceptance_binds_openapi_bytes_and_provenance_to_source() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "build_openapi_release_provenance" in text
    assert "render_openapi_release_provenance" in text
    assert 'os.environ["SOURCE_REVISION"]' in text
    assert "target/openapi-release-provenance.json" in text
    assert "clearfolio-buyer-connector.openapi.yaml" in text
    assert "openapi-release-provenance.json" in text


def test_release_acceptance_persists_byte_digests_without_publish_authority() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "sha256sum" in text
    assert "release-manifest.sha256" in text
    assert "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a" in text
    assert "if-no-files-found: error" in text
    assert "contents: write" not in text
    assert "gh release" not in text

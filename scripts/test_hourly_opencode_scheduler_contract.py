"""Executable contracts for the hourly PR and product-development schedulers."""

from __future__ import annotations

import re
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
PRODUCT_WORKFLOW = (
    REPOSITORY_ROOT / ".github" / "workflows" / "hourly-product-development.yml"
)
PR_WORKFLOW = REPOSITORY_ROOT / ".github" / "workflows" / "hourly-pr-maintenance.yml"
OPERATOR_GUIDE = REPOSITORY_ROOT / "docs" / "operations" / "hourly-development.md"
OPENCODE_VERSION = "1.18.13"
OPENCODE_SHA256 = "8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937"
CENTRAL_WORKFLOW_COMMIT = "74e54255ec903e3ba5f920859b656fe2defcb057"


def _read(path: Path) -> str:
    """Return one required repository file as UTF-8 text."""
    return path.read_text(encoding="utf-8")


def test_hourly_product_scheduler_uses_only_pinned_opencode_and_nvidia_nim() -> None:
    """Keep autonomous product model execution off Copilot and mutable tooling."""
    workflow = _read(PRODUCT_WORKFLOW)

    assert re.search(r'cron:\s*["\']23 \* \* \* \*["\']', workflow)
    assert "workflow_dispatch:" in workflow
    assert "NVIDIA_API_KEY: ${{ secrets.NVIDIA_NIM_API_KEY }}" in workflow
    assert "COPILOT_GITHUB_TOKEN" not in workflow
    assert "OPENAI_API_KEY" not in workflow
    assert "openai/codex-action@" not in workflow
    assert f'OPENCODE_VERSION: "{OPENCODE_VERSION}"' in workflow
    assert f'OPENCODE_SHA256: "{OPENCODE_SHA256}"' in workflow
    assert "opencode-linux-x64.tar.gz" in workflow
    assert "sha256sum --check" in workflow
    assert "opencode run --auto" not in workflow
    assert "opencode run --model" in workflow


def test_credentialed_model_step_cannot_execute_or_publish_repository_code() -> None:
    """Separate the model credential from repository execution and write authority."""
    workflow = _read(PRODUCT_WORKFLOW)
    model_section = workflow.split("- name: Run bounded OpenCode maintainer", 1)[1].split(
        "- name: Reject model credential disclosure", 1
    )[0]

    assert 'OPENCODE_DISABLE_AUTOUPDATE: "true"' in workflow
    assert 'OPENCODE_DISABLE_MODELS_FETCH: "true"' in workflow
    assert 'OPENCODE_DISABLE_DEFAULT_PLUGINS: "true"' in workflow
    assert '"external_directory":"deny"' in workflow
    assert '"webfetch":"deny"' in workflow
    assert '"websearch":"deny"' in workflow
    assert '"question":"deny"' in workflow
    assert '"task":"deny"' in workflow
    assert '"skill":"deny"' in workflow
    assert "mvn " not in model_section
    assert "python -m pytest" not in model_section
    assert "git push" not in model_section
    assert "gh pr create" not in model_section
    assert "Reject model credential disclosure" in workflow
    assert 'grep -R -F -l -- "$NVIDIA_API_KEY"' in workflow


def test_product_scheduler_is_single_flight_bounded_and_exact_base_safe() -> None:
    """Discard proposals when another PR or protected-base change races the run."""
    workflow = " ".join(_read(PRODUCT_WORKFLOW).replace("\\\n", "").split())
    paginated_query = (
        'gh api "repos/${GITHUB_REPOSITORY}/pulls?state=open&per_page=100" '
        "--paginate --slurp --jq 'map(length) | add // 0'"
    )

    assert workflow.count(paginated_query) >= 3
    assert "MAX_CHANGED_FILES: \"20\"" in workflow
    assert "MAX_DIFF_BYTES: \"200000\"" in workflow
    assert "git diff --check" in workflow
    assert "git apply --check" in workflow
    assert "EXPECTED_BASE_SHA" in workflow
    assert "Discarding" in workflow
    assert "src/main/**" in workflow
    assert "src/test/**" in workflow
    assert ".github/**" in workflow
    assert "scripts/**" in workflow


def test_product_scheduler_includes_new_files_in_the_immutable_patch() -> None:
    """Preserve bounded new tests, source, and documentation in proposal evidence."""
    workflow = _read(PRODUCT_WORKFLOW)
    package_section = workflow.split(
        "- name: Enforce change boundary and package proposal", 1
    )[1].split("- name: Upload immutable proposal", 1)[0]

    assert "git ls-files --others --exclude-standard -z" in package_section
    assert 'git add --intent-to-add -- "$path"' in package_section
    assert "git diff --name-only -z --diff-filter=ACMRTUXB" in package_section
    assert package_section.index("git ls-files --others") < package_section.index(
        "git diff --name-only"
    )


def test_product_scheduler_rejects_binary_proposals() -> None:
    """Keep opaque binary payloads outside autonomous source and evidence changes."""
    workflow = _read(PRODUCT_WORKFLOW)
    package_section = workflow.split(
        "- name: Enforce change boundary and package proposal", 1
    )[1].split("- name: Upload immutable proposal", 1)[0]

    assert "git diff --numstat" in package_section
    assert "Binary changes are outside the bounded workflow." in package_section


def test_product_scheduler_protects_build_and_dependency_metadata() -> None:
    """Prevent model proposals from changing executable build or dependency inputs."""
    workflow = _read(PRODUCT_WORKFLOW)
    model_section = workflow.split("- name: Run bounded OpenCode maintainer", 1)[1].split(
        "- name: Reject model credential disclosure", 1
    )[0]
    package_section = workflow.split(
        "- name: Enforce change boundary and package proposal", 1
    )[1].split("- name: Upload immutable proposal", 1)[0]

    assert '"pom.xml":"allow"' not in model_section
    assert "pom.xml)" in package_section
    assert "Protected automation or build path changed" in package_section


def test_uncredentialed_verifier_runs_complete_repository_acceptance() -> None:
    """Require the normal Java and buyer-evidence gates before publication."""
    workflow = _read(PRODUCT_WORKFLOW)
    verifier = workflow.split("\n  verify:\n", 1)[1].split("\n  publish:\n", 1)[0]

    assert "NVIDIA_NIM_API_KEY" not in verifier
    assert "mvn -B --no-transfer-progress verify" in verifier
    assert "python -m pytest -q scripts" in verifier
    assert "--require-hashes -r requirements-test.txt" in verifier
    assert "actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c" in verifier


def test_publisher_uses_dedicated_app_and_creates_draft_only() -> None:
    """Prevent the model and default workflow token from receiving write authority."""
    workflow = _read(PRODUCT_WORKFLOW)
    publisher = workflow.split("\n  publish:\n", 1)[1]
    token_step = publisher.split(
        "- name: Mint dedicated maintainer App token only for publication", 1
    )[1].split("- name: Create a draft pull request without auto-merge", 1)[0]

    assert "actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1" in publisher
    assert "CLEARFOLIO_MAINTAINER_APP_CLIENT_ID" in publisher
    assert "CLEARFOLIO_MAINTAINER_APP_PRIVATE_KEY" in publisher
    assert "permission-contents: write" in token_step
    assert "permission-pull-requests: write" in token_step
    assert set(re.findall(r"permission-([a-z-]+):", token_step)) == {
        "contents",
        "pull-requests",
    }
    assert "GH_TOKEN: ${{ steps.maintainer_app.outputs.token }}" in publisher
    assert "gh pr create" in publisher
    assert "--draft" in publisher
    assert "gh pr merge" not in publisher
    assert "enable-auto-merge" not in publisher


def test_pr_scheduler_preserves_central_review_agent_credentials() -> None:
    """Reuse the reviewed central PR loops without remapping reviewer secrets."""
    workflow = _read(PR_WORKFLOW)

    assert re.search(r'cron:\s*["\']7 \* \* \* \*["\']', workflow)
    assert (
        "ContextualWisdomLab/.github/.github/workflows/"
        f"pr-review-fix-scheduler.yml@{CENTRAL_WORKFLOW_COMMIT}"
    ) in workflow
    assert (
        "ContextualWisdomLab/.github/.github/workflows/"
        f"pr-review-merge-scheduler.yml@{CENTRAL_WORKFLOW_COMMIT}"
    ) in workflow
    assert workflow.count("secrets: inherit") == 2
    assert "NVIDIA_NIM_API_KEY" not in workflow
    assert "COPILOT_GITHUB_TOKEN" not in workflow


def test_operator_guide_records_identity_boundaries_and_prerequisites() -> None:
    """Make the scheduler's trust, secret, and exact-head behavior auditable."""
    guide = _read(OPERATOR_GUIDE)

    assert f"OpenCode {OPENCODE_VERSION}" in guide
    assert f"`{OPENCODE_SHA256}`" in guide
    assert "`NVIDIA_NIM_API_KEY`" in guide
    assert "`NVIDIA_API_KEY`" in guide
    assert "`CLEARFOLIO_MAINTAINER_APP_CLIENT_ID`" in guide
    assert "`CLEARFOLIO_MAINTAINER_APP_PRIVATE_KEY`" in guide
    assert "without `--auto`" in guide
    assert "fails closed" in guide
    assert "credential-free verifier" in guide
    assert "independent approval" in guide
    assert "COPILOT_GITHUB_TOKEN" not in guide

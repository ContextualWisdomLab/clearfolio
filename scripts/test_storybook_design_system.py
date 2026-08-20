from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CSS = ROOT / "src/main/resources/static/assets/viewer/viewer.css"
TOKENS = ROOT / "design/tokens/clearfolio.tokens.json"
STORY = ROOT / "design/storybook/viewer-states.stories.js"
MAIN = ROOT / ".storybook/main.js"
PREVIEW = ROOT / ".storybook/preview.js"
VITEST_SETUP = ROOT / ".storybook/vitest.setup.js"
VITEST_CONFIG = ROOT / "vitest.config.js"
PACKAGE = ROOT / "package.json"
WORKFLOW = ROOT / ".github/workflows/storybook.yml"

REQUIRED_STATES = {
    "Loading",
    "Ready",
    "Failed",
    "NotFound",
    "InvalidDocument",
    "NetworkError",
    "KeyboardFocus",
    "BusyDisabled",
    "MobileLoading",
    "TabletReady",
}
REQUIRED_TOKENS = {
    "brand-blue",
    "ink",
    "muted",
    "bg",
    "panel",
    "line",
    "danger",
    "focus",
}


def _runtime_tokens() -> dict[str, str]:
    css = CSS.read_text(encoding="utf-8")
    root = re.search(r":root\s*\{(?P<body>.*?)\n\}", css, re.DOTALL)
    assert root is not None
    return {
        name: value.strip().lower()
        for name, value in re.findall(r"--([a-z0-9-]+)\s*:\s*([^;]+);", root.group("body"))
        if name in REQUIRED_TOKENS
    }


def _hex_from_srgb(components: list[float]) -> str:
    channels = [round(component * 255) for component in components]
    assert all(0 <= channel <= 255 for channel in channels)
    return "#" + "".join(f"{channel:02x}" for channel in channels)


def test_storybook_authority_files_exist() -> None:
    for path in (
        TOKENS,
        STORY,
        MAIN,
        PREVIEW,
        VITEST_SETUP,
        VITEST_CONFIG,
        PACKAGE,
        WORKFLOW,
    ):
        assert path.is_file(), f"missing executable design-system authority: {path.relative_to(ROOT)}"


def test_dtcg_projection_matches_runtime_css() -> None:
    payload = json.loads(TOKENS.read_text(encoding="utf-8"))
    colors = payload["color"]
    runtime = _runtime_tokens()
    assert set(runtime) == REQUIRED_TOKENS
    assert set(colors) == REQUIRED_TOKENS
    for name, token in colors.items():
        assert token["$type"] == "color"
        value = token["$value"]
        assert value["colorSpace"] == "srgb"
        assert value["alpha"] == 1
        assert _hex_from_srgb(value["components"]) == value["hex"].lower()
        assert value["hex"].lower() == runtime[name]


def test_required_buyer_states_are_named_and_a11y_blocking() -> None:
    story = STORY.read_text(encoding="utf-8")
    exports = set(re.findall(r"export const ([A-Za-z0-9_]+)\s*=", story))
    assert REQUIRED_STATES <= exports
    preview = PREVIEW.read_text(encoding="utf-8")
    assert "test: 'error'" in preview or 'test: "error"' in preview
    assert "wcag22aa" in preview
    assert "prefers-reduced-motion" in story
    assert "forced-colors" in story
    assert "viewer.css" in preview


def test_responsive_stories_use_storybook_10_viewport_globals() -> None:
    preview = PREVIEW.read_text(encoding="utf-8")
    story = STORY.read_text(encoding="utf-8")
    assert "viewport:" in preview
    assert "clearfolioMobile" in preview
    assert "width: '390px'" in preview
    assert "clearfolioTablet" in preview
    assert "width: '768px'" in preview
    assert "viewport: { value: 'clearfolioMobile', isRotated: false }" in story
    assert "viewport: { value: 'clearfolioTablet', isRotated: false }" in story
    assert "defaultViewport" not in story
    assert "width: '390px'" not in story
    assert "width: '768px'" not in story


def test_storybook_uses_current_browser_test_path() -> None:
    setup = VITEST_SETUP.read_text(encoding="utf-8")
    config = VITEST_CONFIG.read_text(encoding="utf-8")
    assert "@storybook/addon-a11y/preview" in setup
    assert "setProjectAnnotations" in setup
    assert "@storybook/addon-vitest/vitest-plugin" in config
    assert "@vitest/browser-playwright" in config
    assert "chromium" in config
    assert "headless: true" in config


def test_storybook_is_development_only_and_has_build_test_commands() -> None:
    package = json.loads(PACKAGE.read_text(encoding="utf-8"))
    assert "dependencies" not in package or not package["dependencies"]
    dev = package["devDependencies"]
    for dependency in (
        "storybook",
        "@storybook/addon-a11y",
        "@storybook/addon-vitest",
        "@storybook/web-components-vite",
    ):
        assert dev[dependency].startswith("10.5")
    assert dev["vitest"].startswith("4.1")
    assert dev["@vitest/browser-playwright"].startswith("4.1")
    scripts = package["scripts"]
    assert "storybook build" in scripts["build-storybook"]
    assert "vitest" in scripts["test-storybook"]


def test_story_fixtures_exclude_customer_authority() -> None:
    story = STORY.read_text(encoding="utf-8").lower()
    forbidden = (
        "bearer ",
        "authorization",
        "tenant_id",
        "signed_url",
        "access_token",
        "refresh_token",
    )
    assert not any(term in story for term in forbidden)
    assert "00000000-0000-0000-0000-000000000000" in story


def test_storybook_workflow_binds_to_exact_pr_head() -> None:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    assert "github.event.pull_request.head.sha" in workflow
    assert "git rev-parse HEAD" in workflow
    assert "npm run build-storybook" in workflow
    assert "npm run test-storybook -- --run" in workflow
    assert "playwright install --with-deps chromium" in workflow

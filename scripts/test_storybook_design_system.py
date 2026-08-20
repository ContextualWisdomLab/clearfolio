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
PACKAGE = ROOT / "package.json"

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
        name: value.strip()
        for name, value in re.findall(r"--([a-z0-9-]+)\s*:\s*([^;]+);", root.group("body"))
        if name in REQUIRED_TOKENS
    }


def test_storybook_authority_files_exist() -> None:
    for path in (TOKENS, STORY, MAIN, PREVIEW, PACKAGE):
        assert path.is_file(), f"missing executable design-system authority: {path.relative_to(ROOT)}"


def test_dtcg_projection_matches_runtime_css() -> None:
    payload = json.loads(TOKENS.read_text(encoding="utf-8"))
    colors = payload["color"]
    runtime = _runtime_tokens()
    assert set(runtime) == REQUIRED_TOKENS
    projected = {name: value["$value"] for name, value in colors.items()}
    assert projected == runtime
    assert all(value["$type"] == "color" for value in colors.values())


def test_required_buyer_states_are_named_and_a11y_blocking() -> None:
    story = STORY.read_text(encoding="utf-8")
    exports = set(re.findall(r"export const ([A-Za-z0-9_]+)\s*=", story))
    assert REQUIRED_STATES <= exports
    preview = PREVIEW.read_text(encoding="utf-8")
    assert "test: 'error'" in preview or 'test: "error"' in preview
    assert "wcag22aa" in preview
    assert "prefers-reduced-motion" in story
    assert "forced-colors" in story


def test_storybook_is_development_only_and_has_build_test_commands() -> None:
    package = json.loads(PACKAGE.read_text(encoding="utf-8"))
    assert "dependencies" not in package or not package["dependencies"]
    dev = package["devDependencies"]
    assert dev["storybook"].startswith("10.5")
    assert dev["@storybook/web-components-vite"].startswith("10.5")
    scripts = package["scripts"]
    assert "storybook build" in scripts["build-storybook"]
    assert "test-storybook" in scripts["test-storybook"]


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

from __future__ import annotations

import json
import re
import unittest
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
PACKAGE_LOCK = ROOT / "package-lock.json"
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
    if root is None:
        raise AssertionError("viewer.css must define a :root token block")
    return {
        name: value.strip().lower()
        for name, value in re.findall(r"--([a-z0-9-]+)\s*:\s*([^;]+);", root.group("body"))
        if name in REQUIRED_TOKENS
    }


def _hex_from_srgb(components: list[float]) -> str:
    channels = [round(component * 255) for component in components]
    if not all(0 <= channel <= 255 for channel in channels):
        raise AssertionError("sRGB token channels must stay in the 0-255 range")
    return "#" + "".join(f"{channel:02x}" for channel in channels)


class StorybookDesignSystemTests(unittest.TestCase):
    def test_storybook_authority_files_exist(self) -> None:
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
            self.assertTrue(
                path.is_file(),
                f"missing executable design-system authority: {path.relative_to(ROOT)}",
            )

    def test_dtcg_projection_matches_runtime_css(self) -> None:
        payload = json.loads(TOKENS.read_text(encoding="utf-8"))
        colors = payload["color"]
        runtime = _runtime_tokens()
        self.assertEqual(set(runtime), REQUIRED_TOKENS)
        self.assertEqual(set(colors), REQUIRED_TOKENS)
        for name, token in colors.items():
            self.assertEqual(token["$type"], "color")
            value = token["$value"]
            self.assertEqual(value["colorSpace"], "srgb")
            self.assertEqual(value["alpha"], 1)
            self.assertEqual(_hex_from_srgb(value["components"]), value["hex"].lower())
            self.assertEqual(value["hex"].lower(), runtime[name])

    def test_srgb_projection_rejects_out_of_range_components(self) -> None:
        for components in ([-0.001, 0.0, 0.0], [1.001, 0.0, 0.0]):
            with self.subTest(components=components):
                with self.assertRaises(AssertionError):
                    _hex_from_srgb(components)

    def test_required_buyer_states_are_named_and_a11y_blocking(self) -> None:
        story = STORY.read_text(encoding="utf-8")
        exports = set(re.findall(r"export const ([A-Za-z0-9_]+)\s*=", story))
        self.assertTrue(REQUIRED_STATES <= exports)
        preview = PREVIEW.read_text(encoding="utf-8")
        self.assertTrue("test: 'error'" in preview or 'test: "error"' in preview)
        self.assertIn("wcag22aa", preview)
        self.assertIn("prefers-reduced-motion", story)
        self.assertIn("forced-colors", story)
        self.assertIn("viewer.css", preview)

    def test_responsive_stories_use_storybook_10_viewport_globals(self) -> None:
        preview = PREVIEW.read_text(encoding="utf-8")
        story = STORY.read_text(encoding="utf-8")
        self.assertIn("MINIMAL_VIEWPORTS", preview)
        self.assertIn("storybook/viewport", preview)
        self.assertIn("viewport: {", preview)
        self.assertIn("options: MINIMAL_VIEWPORTS", preview)
        self.assertIn("viewport: { value: 'mobile1', isRotated: false }", story)
        self.assertIn("viewport: { value: 'tablet', isRotated: false }", story)
        self.assertNotIn("defaultViewport", story)
        self.assertNotIn("width: '390px'", story)
        self.assertNotIn("width: '768px'", story)

    def test_storybook_uses_current_browser_test_path(self) -> None:
        setup = VITEST_SETUP.read_text(encoding="utf-8")
        config = VITEST_CONFIG.read_text(encoding="utf-8")
        self.assertIn("@storybook/addon-a11y/preview", setup)
        self.assertIn("setProjectAnnotations", setup)
        self.assertIn("@storybook/addon-vitest/vitest-plugin", config)
        self.assertIn("@vitest/browser-playwright", config)
        self.assertIn("chromium", config)
        self.assertIn("headless: true", config)

    def test_storybook_is_development_only_and_has_build_test_commands(self) -> None:
        package = json.loads(PACKAGE.read_text(encoding="utf-8"))
        self.assertFalse(package.get("dependencies"))
        dev = package["devDependencies"]
        for dependency in (
            "storybook",
            "@storybook/addon-a11y",
            "@storybook/addon-vitest",
            "@storybook/web-components-vite",
        ):
            self.assertTrue(dev[dependency].startswith("10.5"))
        self.assertTrue(dev["vitest"].startswith("4.1"))
        self.assertTrue(dev["@vitest/browser-playwright"].startswith("4.1"))
        scripts = package["scripts"]
        self.assertIn("storybook build", scripts["build-storybook"])
        self.assertIn("vitest", scripts["test-storybook"])

    def test_storybook_dependencies_are_locked_and_ci_uses_lock_only(self) -> None:
        self.assertTrue(
            PACKAGE_LOCK.is_file(),
            "Storybook transitive dependencies must be reviewable in package-lock.json",
        )
        package = json.loads(PACKAGE.read_text(encoding="utf-8"))
        lock = json.loads(PACKAGE_LOCK.read_text(encoding="utf-8"))
        self.assertEqual(lock["lockfileVersion"], 3)
        self.assertEqual(lock["packages"][""]["devDependencies"], package["devDependencies"])
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertNotIn("npm install --package-lock-only", workflow)
        self.assertIn("npm ci --ignore-scripts --no-audit --no-fund", workflow)

    def test_story_fixtures_exclude_customer_authority(self) -> None:
        story = STORY.read_text(encoding="utf-8").lower()
        forbidden = (
            "bearer ",
            "authorization",
            "tenant_id",
            "signed_url",
            "access_token",
            "refresh_token",
        )
        self.assertFalse(any(term in story for term in forbidden))
        self.assertIn("00000000-0000-0000-0000-000000000000", story)

    def test_storybook_workflow_binds_to_exact_pr_head(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("github.event.pull_request.head.sha", workflow)
        self.assertIn("git rev-parse HEAD", workflow)
        self.assertIn("python3 -m unittest discover -s scripts", workflow)
        self.assertIn("npm run build-storybook", workflow)
        self.assertIn("npm run test-storybook -- --run", workflow)
        self.assertIn("playwright install --with-deps chromium", workflow)


if __name__ == "__main__":
    unittest.main()

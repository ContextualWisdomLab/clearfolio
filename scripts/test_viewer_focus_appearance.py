#!/usr/bin/env python3
"""Verify that the viewer focus indicator stays visible on light and dark surfaces."""

from __future__ import annotations

import re
import unittest
from dataclasses import dataclass
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
VIEWER_CSS = REPOSITORY_ROOT / "src/main/resources/static/assets/viewer/viewer.css"
MINIMUM_CONTRAST = 3.0
MINIMUM_BAND_WIDTH_PX = 2.0


@dataclass(frozen=True)
class FocusBand:
    """Describe one solid, independently visible focus-indicator band."""

    width_px: float
    color_hex: str


def _srgb_channel_to_linear(channel: float) -> float:
    """Convert one normalized sRGB channel to linear-light space."""

    if channel <= 0.04045:
        return channel / 12.92
    return ((channel + 0.055) / 1.055) ** 2.4


def _relative_luminance(color_hex: str) -> float:
    """Return WCAG relative luminance for one six-digit hexadecimal color."""

    match = re.fullmatch(r"#([0-9a-fA-F]{6})", color_hex)
    if match is None:
        raise AssertionError(f"unsupported focus color: {color_hex}")
    digits = match.group(1)
    red, green, blue = (
        int(digits[index : index + 2], 16) / 255.0 for index in (0, 2, 4)
    )
    linear_red, linear_green, linear_blue = (
        _srgb_channel_to_linear(channel) for channel in (red, green, blue)
    )
    return 0.2126 * linear_red + 0.7152 * linear_green + 0.0722 * linear_blue


def _contrast_ratio(first_hex: str, second_hex: str) -> float:
    """Return WCAG contrast ratio for two six-digit hexadecimal colors."""

    first = _relative_luminance(first_hex)
    second = _relative_luminance(second_hex)
    lighter = max(first, second)
    darker = min(first, second)
    return (lighter + 0.05) / (darker + 0.05)


def _mix_with_white(color_hex: str, white_percentage: float) -> str:
    """Resolve the viewer's sRGB color-mix form into one hexadecimal color."""

    match = re.fullmatch(r"#([0-9a-fA-F]{6})", color_hex)
    if match is None:
        raise AssertionError(f"unsupported mix color: {color_hex}")
    weight = white_percentage / 100.0
    digits = match.group(1)
    channels = [int(digits[index : index + 2], 16) for index in (0, 2, 4)]
    mixed = [round(channel * (1.0 - weight) + 255 * weight) for channel in channels]
    return "#" + "".join(f"{channel:02x}" for channel in mixed)


def _custom_property(css: str, property_name: str) -> str:
    """Return one root custom-property value from the viewer stylesheet."""

    match = re.search(rf"{re.escape(property_name)}\s*:\s*([^;]+);", css)
    if match is None:
        raise AssertionError(f"missing CSS property: {property_name}")
    return match.group(1).strip()


def _focus_block(css: str) -> str:
    """Return the top-level focus-visible declaration block."""

    match = re.search(r":focus-visible\s*\{(?P<body>.*?)\}", css, re.DOTALL)
    if match is None:
        raise AssertionError("missing :focus-visible block")
    return match.group("body")


def _resolve_focus_color(css: str, expression: str) -> str:
    """Resolve the limited color syntax used by the focus contract."""

    value = expression.strip()
    variable_match = re.fullmatch(r"var\((--[a-zA-Z0-9_-]+)\)", value)
    if variable_match is not None:
        return _custom_property(css, variable_match.group(1))
    mix_match = re.fullmatch(
        r"color-mix\(in\s+srgb,\s*var\((--[a-zA-Z0-9_-]+)\),\s*#ffffff\s+([0-9.]+)%\)",
        value,
        re.IGNORECASE,
    )
    if mix_match is not None:
        base_color = _custom_property(css, mix_match.group(1))
        return _mix_with_white(base_color, float(mix_match.group(2)))
    if re.fullmatch(r"#[0-9a-fA-F]{6}", value):
        return value.lower()
    raise AssertionError(f"unsupported focus color expression: {value}")


def _outline_offset_px(block: str) -> float:
    """Return the non-negative outline offset that exposes an inner shadow band."""

    match = re.search(
        r"outline-offset\s*:\s*([+-]?[0-9.]+)px\s*;",
        block,
        re.IGNORECASE,
    )
    if match is None:
        raise AssertionError("focus-visible must define an outline-offset")
    offset = float(match.group(1))
    if offset < 0.0:
        raise AssertionError("focus-visible outline-offset must not be negative")
    return offset


def _focus_bands(css: str) -> list[FocusBand]:
    """Return non-overlapping solid bands visible in the focus-visible rule."""

    block = _focus_block(css)
    outlines = re.findall(
        r"outline\s*:\s*([0-9.]+)px\s+solid\s+([^;]+);",
        block,
        re.IGNORECASE,
    )
    if not outlines:
        raise AssertionError("focus-visible must define a solid outline")
    outline_width, outline_expression = outlines[-1]
    bands = [
        FocusBand(float(outline_width), _resolve_focus_color(css, outline_expression))
    ]

    shadow_match = re.search(
        r"box-shadow\s*:\s*0\s+0\s+0\s+([0-9.]+)px\s+(#[0-9a-fA-F]{6})\s*;",
        block,
        re.IGNORECASE,
    )
    if shadow_match is not None:
        shadow_spread = float(shadow_match.group(1))
        exposed_shadow_width = min(shadow_spread, _outline_offset_px(block))
        if exposed_shadow_width > 0.0:
            bands.append(FocusBand(exposed_shadow_width, shadow_match.group(2).lower()))
    return bands


class ViewerFocusAppearanceTest(unittest.TestCase):
    """Keep keyboard focus visible across light and dark viewer surfaces."""

    def test_focus_indicator_has_qualifying_band_on_light_and_dark_backgrounds(self) -> None:
        """Require a >=2px, >=3:1 band for both white and black backgrounds."""

        css = VIEWER_CSS.read_text(encoding="utf-8")
        bands = _focus_bands(css)

        for background in ("#ffffff", "#000000"):
            with self.subTest(background=background):
                qualifying_width = sum(
                    band.width_px
                    for band in bands
                    if _contrast_ratio(band.color_hex, background) >= MINIMUM_CONTRAST
                )
                self.assertGreaterEqual(
                    qualifying_width,
                    MINIMUM_BAND_WIDTH_PX,
                    (
                        f"focus indicator needs at least {MINIMUM_BAND_WIDTH_PX:g}px "
                        f"at {MINIMUM_CONTRAST:g}:1 against {background}; "
                        f"bands={bands}"
                    ),
                )

    def test_focus_band_parser_does_not_double_count_overlapping_shadow(self) -> None:
        """Count only shadow pixels exposed before an overlapping outline begins."""

        css = """
        :focus-visible {
          outline: 3px solid #000000;
          outline-offset: 1px;
          box-shadow: 0 0 0 3px #ffffff;
        }
        """

        self.assertEqual(
            [FocusBand(3.0, "#000000"), FocusBand(1.0, "#ffffff")],
            _focus_bands(css),
        )


if __name__ == "__main__":
    unittest.main()

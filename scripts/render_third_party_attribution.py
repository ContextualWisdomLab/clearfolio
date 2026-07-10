#!/usr/bin/env python3
"""Render a buyer data-room third-party attribution file from a CycloneDX SBOM."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def component_name(component: dict) -> str:
    group = component.get("group")
    name = component.get("name", "<unknown>")
    return f"{group}:{name}" if group else name


def component_licenses(component: dict) -> list[str]:
    values: list[str] = []
    for item in component.get("licenses", []):
        license_data = item.get("license") or {}
        value = license_data.get("id") or license_data.get("name")
        if value:
            values.append(value)
    return values or ["NOASSERTION"]


def escape_table_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


def render_markdown(sbom: dict) -> str:
    components = sorted(
        sbom.get("components", []),
        key=lambda item: (
            component_name(item).lower(),
            str(item.get("version", "")).lower(),
            str(item.get("purl", "")).lower(),
        ),
    )
    sbom_format = sbom.get("bomFormat", "CycloneDX")
    spec_version = sbom.get("specVersion", "unknown")
    lines = [
        "# Third-Party Attribution",
        "",
        "This buyer data-room attribution package is generated from the current",
        "CycloneDX SBOM. It is engineering evidence, not legal advice.",
        "",
        "## Summary",
        "",
        f"- SBOM format: {sbom_format} {spec_version}",
        f"- Component count: {len(components)}",
        "- License metadata source: CycloneDX component license entries",
        "",
        "## Components",
        "",
        "| Component | Version | License metadata | Package URL |",
        "| --- | --- | --- | --- |",
    ]
    for item in components:
        name = escape_table_cell(component_name(item))
        version = escape_table_cell(str(item.get("version", "<unknown>")))
        licenses = escape_table_cell(", ".join(component_licenses(item)))
        purl = escape_table_cell(str(item.get("purl", "<missing-purl>")))
        lines.append(f"| {name} | {version} | {licenses} | `{purl}` |")
    lines.extend([
        "",
        "## Release Note",
        "",
        "Keep this file aligned with the generated SBOM. If the SBOM changes, rerun",
        "`scripts/render_third_party_attribution.py` and the attribution drift check.",
        "",
    ])
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sbom", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if the output file does not match the SBOM-derived attribution.",
    )
    args = parser.parse_args(argv)

    markdown = render_markdown(load_json(args.sbom))
    if args.check:
        actual = args.output.read_text(encoding="utf-8") if args.output.exists() else ""
        if actual != markdown:
            print(f"{args.output} is out of date; rerun attribution renderer.", file=sys.stderr)
            return 2
        print(f"{args.output} is up to date.")
        return 0

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    print(f"Wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

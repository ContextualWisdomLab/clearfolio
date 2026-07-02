#!/usr/bin/env python3
"""Check a CycloneDX SBOM against Clearfolio's engineering license policy."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def component_label(component: dict) -> str:
    group = component.get("group")
    name = component.get("name", "<unknown>")
    version = component.get("version", "<unknown>")
    prefix = f"{group}:" if group else ""
    return f"{prefix}{name}@{version}"


def component_licenses(component: dict) -> list[str]:
    values: list[str] = []
    for item in component.get("licenses", []):
        license_data = item.get("license") or {}
        value = license_data.get("id") or license_data.get("name")
        if value:
            values.append(value)
    return values


def evaluate(sbom: dict, policy: dict, require_no_review: bool) -> dict:
    allowed = set(policy.get("allowedLicenses", []))
    review_by_purl = {
        item["purl"]: item.get("reason", "legal review required")
        for item in policy.get("reviewRequiredComponents", [])
    }
    allowed_components: list[dict] = []
    review_components: list[dict] = []
    violations: list[dict] = []

    for component in sbom.get("components", []):
        purl = component.get("purl")
        label = component_label(component)
        licenses = component_licenses(component)
        if not licenses:
            violations.append({
                "component": label,
                "purl": purl,
                "reason": "missing license metadata",
            })
            continue

        unknown = [value for value in licenses if value not in allowed]
        if not unknown:
            allowed_components.append({
                "component": label,
                "purl": purl,
                "licenses": licenses,
            })
            continue

        if purl in review_by_purl:
            review_components.append({
                "component": label,
                "purl": purl,
                "licenses": licenses,
                "reason": review_by_purl[purl],
            })
            continue

        violations.append({
            "component": label,
            "purl": purl,
            "licenses": licenses,
            "reason": "license outside allowlist and not in review-required components",
        })

    if require_no_review:
        for item in review_components:
            violations.append({
                "component": item["component"],
                "purl": item["purl"],
                "licenses": item["licenses"],
                "reason": "review-required component blocks buyer-release mode",
            })

    return {
        "bomFormat": sbom.get("bomFormat"),
        "specVersion": sbom.get("specVersion"),
        "componentCount": len(sbom.get("components", [])),
        "allowedCount": len(allowed_components),
        "reviewRequiredCount": len(review_components),
        "violationCount": len(violations),
        "reviewRequiredComponents": review_components,
        "violations": violations,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sbom", required=True, type=Path)
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--summary", type=Path)
    parser.add_argument(
        "--require-no-review",
        action="store_true",
        help="Fail when any review-required component remains open.",
    )
    args = parser.parse_args(argv)

    result = evaluate(load_json(args.sbom), load_json(args.policy), args.require_no_review)
    output = json.dumps(result, indent=2, sort_keys=True)
    if args.summary:
        args.summary.write_text(output + "\n", encoding="utf-8")
    print(output)
    return 0 if result["violationCount"] == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

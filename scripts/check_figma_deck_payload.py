#!/usr/bin/env python3
"""Check the Figma Slides generation payload before buyer use."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def text_contains(values: list[str], needle: str) -> bool:
    return any(needle.lower() in str(value).lower() for value in values)


def check_payload(payload: dict) -> dict:
    errors: list[str] = []
    title = str(payload.get("title", "")).strip()
    description = str(payload.get("description", "")).strip()
    objectives = payload.get("objectives", [])
    outline = payload.get("outline", [])

    if not title:
        errors.append("title is required")
    if "figma code connect" not in description.lower() or "not" not in description.lower():
        errors.append("description must state that Figma Code Connect is not used")
    if not isinstance(objectives, list) or len(objectives) < 3:
        errors.append("at least 3 objectives are required")
        objectives = []
    if not isinstance(outline, list) or len(outline) < 3:
        errors.append("at least 3 outline slides are required")
        outline = []

    subjects = [str(slide.get("subject", "")) for slide in outline if isinstance(slide, dict)]
    roles = [str(slide.get("role", "")) for slide in outline if isinstance(slide, dict)]
    contents = [
        str(item)
        for slide in outline
        if isinstance(slide, dict)
        for item in slide.get("content", [])
    ]

    if "closing" not in roles or not text_contains(subjects + contents, "claim boundary"):
        errors.append("outline must include a claim boundary closing slide")
    if not text_contains(subjects + contents, "readiness scorecard"):
        errors.append("outline must include the buyer readiness scorecard")
    objective_text = [str(objective) for objective in objectives]

    if not text_contains(subjects + contents + objective_text, "discount risk") and not text_contains(
        subjects + contents + objective_text,
        "partial gates",
    ):
        errors.append("outline must preserve discount risks")
    if not text_contains(contents, "not a valuation opinion"):
        errors.append("outline must state that this is not a valuation opinion")

    for index, slide in enumerate(outline, start=1):
        if not isinstance(slide, dict):
            errors.append(f"slide {index} must be an object")
            continue
        for field in ("role", "subject", "purpose", "visuals", "content"):
            if field not in slide:
                errors.append(f"slide {index} missing {field}")

    return {
        "title": title,
        "slideCount": len(outline),
        "objectiveCount": len(objectives),
        "errors": errors,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--payload", required=True, type=Path)
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args(argv)

    result = check_payload(load_json(args.payload))
    output = json.dumps(result, indent=2, sort_keys=True)
    if args.summary:
        args.summary.write_text(output + "\n", encoding="utf-8")
    print(output)
    return 0 if not result["errors"] else 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

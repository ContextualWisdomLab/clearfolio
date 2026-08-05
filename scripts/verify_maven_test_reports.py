#!/usr/bin/env python3
"""Fail closed when Maven test reports are missing, empty, malformed, or skipped."""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Final

REPORT_PATTERN: Final[str] = "TEST-*.xml"


class ReportGateError(RuntimeError):
    """Describe Maven test evidence that is unsafe to accept."""


def _local_name(tag: str) -> str:
    """Return an XML element name without an optional namespace prefix."""
    return tag.rsplit("}", 1)[-1]


def _non_negative_count(report: Path, suite: ET.Element, attribute: str) -> int:
    """Read one required non-negative integer test-suite attribute."""
    raw_value = suite.get(attribute, "0")
    try:
        value = int(raw_value)
    except ValueError as error:
        raise ReportGateError(
            f"{report} testsuite {attribute} must be a non-negative integer, got {raw_value!r}"
        ) from error
    if value < 0:
        raise ReportGateError(
            f"{report} testsuite {attribute} must be a non-negative integer, got {raw_value!r}"
        )
    return value


def _suite_elements(report: Path) -> list[ET.Element]:
    """Parse one report and return every concrete test-suite element."""
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError as error:
        raise ReportGateError(f"{report} is not valid XML: {error}") from error

    if _local_name(root.tag) == "testsuite":
        return [root]
    suites = [element for element in root.iter() if _local_name(element.tag) == "testsuite"]
    if not suites:
        raise ReportGateError(f"{report} contains no testsuite element")
    return suites


def _verify_report_family(
    report_directory: Path,
    *,
    display_name: str,
    required: bool,
) -> tuple[int, int] | None:
    """Verify one Surefire or Failsafe report family and return its totals."""
    reports = sorted(report_directory.glob(REPORT_PATTERN))
    if not reports:
        if required:
            raise ReportGateError(
                f"{display_name} produced no TEST-*.xml reports in {report_directory}"
            )
        return None

    total_tests = 0
    total_skipped = 0
    for report in reports:
        for suite in _suite_elements(report):
            total_tests += _non_negative_count(report, suite, "tests")
            total_skipped += _non_negative_count(report, suite, "skipped")

    if total_tests == 0:
        raise ReportGateError(f"{display_name} executed zero tests")
    if total_skipped > 0:
        noun = "test" if total_skipped == 1 else "tests"
        raise ReportGateError(f"{display_name} reported {total_skipped} skipped {noun}")
    return total_tests, total_skipped


def verify_maven_reports(target_directory: Path) -> dict[str, tuple[int, int]]:
    """Require executed, zero-skip Surefire evidence and validate Failsafe when present.

    Args:
        target_directory: Maven ``target`` directory containing report subdirectories.

    Returns:
        A mapping from the lowercase report-family name to ``(tests, skipped)`` totals.

    Raises:
        ReportGateError: If required evidence is absent or any report is unsafe.
    """
    summary: dict[str, tuple[int, int]] = {}
    surefire = _verify_report_family(
        target_directory / "surefire-reports",
        display_name="Surefire",
        required=True,
    )
    if surefire is None:  # Defensive assertion for static type narrowing.
        raise ReportGateError("Surefire report verification returned no summary")
    summary["surefire"] = surefire

    failsafe = _verify_report_family(
        target_directory / "failsafe-reports",
        display_name="Failsafe",
        required=False,
    )
    if failsafe is not None:
        summary["failsafe"] = failsafe
    return summary


def main() -> int:
    """Run the Maven report gate as a command-line program."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--target-directory",
        type=Path,
        default=Path("target"),
        help="Maven target directory to inspect (default: target)",
    )
    arguments = parser.parse_args()
    try:
        summary = verify_maven_reports(arguments.target_directory)
    except ReportGateError as error:
        parser.exit(1, f"Maven test-report gate failed: {error}\n")

    for family, (tests, skipped) in summary.items():
        print(f"{family}: tests={tests} skipped={skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

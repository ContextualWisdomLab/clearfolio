#!/usr/bin/env python3
"""Fail closed when Maven test reports are missing, empty, malformed, or skipped."""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Final

REPORT_PATTERN: Final[str] = "TEST-*.xml"
MAX_REPORT_BYTES: Final[int] = 16 * 1024 * 1024
_FORBIDDEN_XML_DECLARATIONS: Final[tuple[str, ...]] = ("<!DOCTYPE", "<!ENTITY")


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


def _read_bounded_report(report: Path) -> bytes:
    """Read one UTF-8 report within the byte and XML-declaration limits."""
    try:
        with report.open("rb") as report_stream:
            report_bytes = report_stream.read(MAX_REPORT_BYTES + 1)
    except OSError as error:
        raise ReportGateError(f"{report} cannot be read: {error}") from error
    if len(report_bytes) > MAX_REPORT_BYTES:
        raise ReportGateError(f"{report} exceeds the {MAX_REPORT_BYTES}-byte limit")

    try:
        report_text = report_bytes.decode("utf-8-sig")
    except UnicodeDecodeError as error:
        raise ReportGateError(f"{report} must use UTF-8 XML encoding: {error}") from error
    if "\x00" in report_text:
        raise ReportGateError(f"{report} contains a forbidden NUL byte")

    uppercase_text = report_text.upper()
    if any(marker in uppercase_text for marker in _FORBIDDEN_XML_DECLARATIONS):
        raise ReportGateError(f"{report} contains a forbidden DTD or entity declaration")
    return report_bytes


def _suite_elements(report: Path) -> list[ET.Element]:
    """Parse one bounded UTF-8 entity-free report and return concrete test suites."""
    report_bytes = _read_bounded_report(report)
    try:
        # Input is bounded, strict UTF-8, NUL-free, and pre-scanned for DTD/entity declarations.
        root = ET.fromstring(report_bytes)  # nosemgrep: python.lang.security.use-defused-xml-parse.use-defused-xml-parse
    except ET.ParseError as error:
        raise ReportGateError(f"{report} is not valid XML: {error}") from error

    if _local_name(root.tag) == "testsuite":
        return [root]
    suites = [element for element in root.iter() if _local_name(element.tag) == "testsuite"]
    if not suites:
        raise ReportGateError(f"{report} contains no testsuite element")
    return suites


def _reported_count_error(display_name: str, count: int, attribute: str) -> ReportGateError:
    """Build one readable failure for nonzero Maven outcome evidence."""
    noun = attribute[:-1] if count == 1 else attribute
    return ReportGateError(f"{display_name} reported {count} test {noun}")


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
    total_failures = 0
    total_errors = 0
    for report in reports:
        for suite in _suite_elements(report):
            total_tests += _non_negative_count(report, suite, "tests")
            total_skipped += _non_negative_count(report, suite, "skipped")
            total_failures += _non_negative_count(report, suite, "failures")
            total_errors += _non_negative_count(report, suite, "errors")

    if total_tests == 0:
        raise ReportGateError(f"{display_name} executed zero tests")
    if total_skipped > 0:
        noun = "test" if total_skipped == 1 else "tests"
        raise ReportGateError(f"{display_name} reported {total_skipped} skipped {noun}")
    if total_failures > 0:
        raise _reported_count_error(display_name, total_failures, "failures")
    if total_errors > 0:
        raise _reported_count_error(display_name, total_errors, "errors")
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

#!/usr/bin/env python3
"""Tests for fail-closed Maven test-report acceptance."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import verify_maven_test_reports as report_gate
from verify_maven_test_reports import ReportGateError, verify_maven_reports


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
CI_WORKFLOW = REPOSITORY_ROOT / ".github" / "workflows" / "ci.yml"


def _write_report(
    directory: Path,
    *,
    filename: str = "TEST-example.xml",
    tests: str = "1",
    skipped: str = "0",
    errors: str = "0",
    failures: str = "0",
) -> None:
    """Write one compact Maven test-suite XML fixture."""
    directory.mkdir(parents=True, exist_ok=True)
    (directory / filename).write_text(
        f'<testsuite tests="{tests}" skipped="{skipped}" '
        f'errors="{errors}" failures="{failures}"/>',
        encoding="utf-8",
    )


class MavenTestReportGateTest(unittest.TestCase):
    """Protect report discovery, non-empty execution, and zero-skip rules."""

    def test_accepts_executed_surefire_reports_without_failsafe_output(self) -> None:
        """Accept a normal unit-test run when every discovered test executed."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            _write_report(target / "surefire-reports", tests="3")

            summary = verify_maven_reports(target)

        self.assertEqual({"surefire": (3, 0)}, summary)

    def test_accepts_executed_surefire_and_failsafe_reports(self) -> None:
        """Apply the same zero-skip rule when integration reports exist."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            _write_report(target / "surefire-reports", tests="2")
            _write_report(target / "failsafe-reports", tests="4")

            summary = verify_maven_reports(target)

        self.assertEqual({"surefire": (2, 0), "failsafe": (4, 0)}, summary)

    def test_accepts_utf8_byte_order_mark(self) -> None:
        """Allow the harmless UTF-8 BOM emitted by some XML writers."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            reports = target / "surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-bom.xml").write_bytes(
                b'\xef\xbb\xbf<testsuite tests="1" skipped="0"/>'
            )

            summary = verify_maven_reports(target)

        self.assertEqual({"surefire": (1, 0)}, summary)

    def test_rejects_missing_surefire_reports(self) -> None:
        """Fail when Maven produced no authoritative unit-test evidence."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            with self.assertRaisesRegex(ReportGateError, "Surefire produced no TEST-.* reports"):
                verify_maven_reports(Path(temporary_directory))

    def test_rejects_zero_executed_tests(self) -> None:
        """Fail when reports exist but contain no executed test cases."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            _write_report(target / "surefire-reports", tests="0")

            with self.assertRaisesRegex(ReportGateError, "Surefire executed zero tests"):
                verify_maven_reports(target)

    def test_rejects_skipped_surefire_tests(self) -> None:
        """Fail when a disabled or conditionally skipped unit test is reported."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            _write_report(target / "surefire-reports", tests="2", skipped="1")

            with self.assertRaisesRegex(ReportGateError, "Surefire reported 1 skipped test"):
                verify_maven_reports(target)

    def test_rejects_skipped_failsafe_tests(self) -> None:
        """Fail when optional integration-test evidence contains a skip."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            _write_report(target / "surefire-reports")
            _write_report(target / "failsafe-reports", skipped="1")

            with self.assertRaisesRegex(ReportGateError, "Failsafe reported 1 skipped test"):
                verify_maven_reports(target)

    def test_rejects_reported_test_failures_and_errors(self) -> None:
        """Do not trust report evidence that contradicts a successful Maven exit."""
        for attribute, singular in (("failures", "failure"), ("errors", "error")):
            with self.subTest(attribute=attribute):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    target = Path(temporary_directory)
                    values = {attribute: "1"}
                    _write_report(target / "surefire-reports", **values)

                    with self.assertRaisesRegex(
                        ReportGateError,
                        f"Surefire reported 1 test {singular}",
                    ):
                        verify_maven_reports(target)

    def test_rejects_invalid_or_negative_report_counts(self) -> None:
        """Treat malformed count metadata as unusable acceptance evidence."""
        for invalid_count in ("not-a-number", "-1"):
            with self.subTest(invalid_count=invalid_count):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    target = Path(temporary_directory)
                    _write_report(target / "surefire-reports", tests=invalid_count)

                    with self.assertRaisesRegex(ReportGateError, "non-negative integer"):
                        verify_maven_reports(target)

    def test_rejects_malformed_xml_and_missing_test_suite(self) -> None:
        """Fail closed for unreadable XML and documents without a test suite."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            reports = target / "surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-malformed.xml").write_text("<testsuite", encoding="utf-8")
            with self.assertRaisesRegex(ReportGateError, "is not valid XML"):
                verify_maven_reports(target)

        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            reports = target / "surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-empty.xml").write_text("<report/>", encoding="utf-8")
            with self.assertRaisesRegex(ReportGateError, "contains no testsuite"):
                verify_maven_reports(target)

    def test_rejects_doctype_and_entity_declarations_before_xml_parsing(self) -> None:
        """Prevent external entities and expansion bombs in UTF-8 report evidence."""
        payloads = (
            b'<!DOCTYPE testsuite SYSTEM "file:///etc/passwd"><testsuite tests="1"/>',
            b'<!ENTITY payload "boom"><testsuite tests="1"/>',
        )
        for payload in payloads:
            with self.subTest(payload=payload[:20]):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    target = Path(temporary_directory)
                    reports = target / "surefire-reports"
                    reports.mkdir(parents=True)
                    (reports / "TEST-unsafe.xml").write_bytes(payload)

                    with self.assertRaisesRegex(ReportGateError, "DTD or entity declaration"):
                        verify_maven_reports(target)

    def test_rejects_utf16_encoded_declaration_bypasses(self) -> None:
        """Reject encodings that could hide dangerous declarations from byte scans."""
        dangerous_xml = (
            '<!DOCTYPE testsuite [<!ENTITY payload SYSTEM "file:///etc/passwd">]>'
            '<testsuite tests="1">&payload;</testsuite>'
        )
        payloads = (
            dangerous_xml.encode("utf-16"),
            dangerous_xml.encode("utf-16-le"),
        )
        for payload in payloads:
            with self.subTest(prefix=payload[:8]):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    target = Path(temporary_directory)
                    reports = target / "surefire-reports"
                    reports.mkdir(parents=True)
                    (reports / "TEST-encoded.xml").write_bytes(payload)

                    with self.assertRaisesRegex(
                        ReportGateError,
                        "UTF-8|NUL byte",
                    ):
                        verify_maven_reports(target)

    def test_rejects_oversized_report_before_xml_parsing(self) -> None:
        """Bound parser memory exposure even when test code writes a large report."""
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory)
            reports = target / "surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-large.xml").write_bytes(b"<testsuite" + b" " * 64 + b"/>")

            with mock.patch.object(report_gate, "MAX_REPORT_BYTES", 32):
                with self.assertRaisesRegex(ReportGateError, "exceeds the 32-byte limit"):
                    verify_maven_reports(target)

    def test_ci_invokes_report_gate_after_maven_verify(self) -> None:
        """Keep the executable report gate in the exact-head Maven job."""
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("python3 scripts/verify_maven_test_reports.py", workflow)
        self.assertLess(
            workflow.index("mvn -B --no-transfer-progress verify"),
            workflow.index("python3 scripts/verify_maven_test_reports.py"),
        )


if __name__ == "__main__":
    unittest.main()

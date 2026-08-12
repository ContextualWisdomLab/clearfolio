"""Protect the intentional single Commons Logging runtime-provider dependency contract."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POM = ROOT / "pom.xml"


def _pom_text() -> str:
    """Return the bounded repository-owned Maven descriptor as UTF-8 text."""
    return POM.read_text(encoding="utf-8")


def _dependency_block(pom: str, group_id: str, artifact_id: str) -> str:
    """Return one dependency block for the requested Maven coordinates."""
    pattern = re.compile(
        r"<dependency>\s*"
        rf"<groupId>{re.escape(group_id)}</groupId>\s*"
        rf"<artifactId>{re.escape(artifact_id)}</artifactId>"
        r".*?</dependency>",
        re.DOTALL,
    )
    match = pattern.search(pom)
    if match is None:
        raise AssertionError(f"missing dependency block: {group_id}:{artifact_id}")
    return match.group(0)


class CommonsLoggingDependencyContractTest(unittest.TestCase):
    """Keep the PDFBox exclusion and Spring logging bridge intent executable."""

    def test_pdfbox_excludes_standalone_commons_logging_provider(self) -> None:
        """Require PDFBox to exclude the duplicate standalone provider."""
        pom = _pom_text()
        pdfbox = _dependency_block(pom, "org.apache.pdfbox", "pdfbox")

        exclusion = re.compile(
            r"<exclusion>\s*"
            r"<groupId>commons-logging</groupId>\s*"
            r"<artifactId>commons-logging</artifactId>\s*"
            r"</exclusion>"
        )
        self.assertIsNotNone(exclusion.search(pdfbox))

    def test_project_does_not_reintroduce_commons_logging_directly(self) -> None:
        """Reject direct standalone Commons Logging and retain the intended backend."""
        pom = _pom_text()

        direct_commons_logging = re.compile(
            r"<dependency>\s*"
            r"<groupId>commons-logging</groupId>\s*"
            r"<artifactId>commons-logging</artifactId>"
        )
        self.assertIsNone(direct_commons_logging.search(pom))
        _dependency_block(pom, "org.springframework.boot", "spring-boot-starter-log4j2")


if __name__ == "__main__":
    unittest.main()

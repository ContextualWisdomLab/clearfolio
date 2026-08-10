import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POM = ROOT / "pom.xml"


def _pom_text() -> str:
    return POM.read_text(encoding="utf-8")


def _dependency_block(pom: str, group_id: str, artifact_id: str) -> str:
    pattern = re.compile(
        r"<dependency>\s*"
        rf"<groupId>{re.escape(group_id)}</groupId>\s*"
        rf"<artifactId>{re.escape(artifact_id)}</artifactId>"
        r".*?</dependency>",
        re.DOTALL,
    )
    match = pattern.search(pom)
    assert match is not None
    return match.group(0)


def test_pdfbox_excludes_standalone_commons_logging_provider() -> None:
    pom = _pom_text()
    pdfbox = _dependency_block(pom, "org.apache.pdfbox", "pdfbox")

    exclusion = re.compile(
        r"<exclusion>\s*"
        r"<groupId>commons-logging</groupId>\s*"
        r"<artifactId>commons-logging</artifactId>\s*"
        r"</exclusion>"
    )
    assert exclusion.search(pdfbox) is not None


def test_project_does_not_reintroduce_commons_logging_directly() -> None:
    pom = _pom_text()

    direct_commons_logging = re.compile(
        r"<dependency>\s*"
        r"<groupId>commons-logging</groupId>\s*"
        r"<artifactId>commons-logging</artifactId>"
    )
    assert direct_commons_logging.search(pom) is None
    _dependency_block(pom, "org.springframework.boot", "spring-boot-starter-log4j2")

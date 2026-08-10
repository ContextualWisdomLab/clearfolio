from pathlib import Path
import xml.etree.ElementTree as ET


MAVEN = {"m": "http://maven.apache.org/POM/4.0.0"}
ROOT = Path(__file__).resolve().parents[1]
POM = ROOT / "pom.xml"


def _coordinates(dependency: ET.Element) -> tuple[str | None, str | None]:
    return (
        dependency.findtext("m:groupId", namespaces=MAVEN),
        dependency.findtext("m:artifactId", namespaces=MAVEN),
    )


def test_pdfbox_excludes_standalone_commons_logging_provider() -> None:
    root = ET.parse(POM).getroot()
    dependencies = root.findall("m:dependencies/m:dependency", MAVEN)

    pdfbox = [
        dependency
        for dependency in dependencies
        if _coordinates(dependency) == ("org.apache.pdfbox", "pdfbox")
    ]
    assert len(pdfbox) == 1

    exclusions = {
        _coordinates(exclusion)
        for exclusion in pdfbox[0].findall("m:exclusions/m:exclusion", MAVEN)
    }
    assert ("commons-logging", "commons-logging") in exclusions


def test_project_does_not_reintroduce_commons_logging_directly() -> None:
    root = ET.parse(POM).getroot()
    dependencies = root.findall("m:dependencies/m:dependency", MAVEN)
    coordinates = {_coordinates(dependency) for dependency in dependencies}

    assert ("commons-logging", "commons-logging") not in coordinates
    assert ("org.springframework.boot", "spring-boot-starter-log4j2") in coordinates

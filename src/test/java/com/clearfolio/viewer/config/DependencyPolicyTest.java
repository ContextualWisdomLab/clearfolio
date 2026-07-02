package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class DependencyPolicyTest {

    @Test
    void pomDoesNotDeclareBroadTikaParserPackageForBuyerRelease() throws Exception {
        Set<String> dependencies = declaredDependencies();

        assertFalse(
                dependencies.contains("org.apache.tika:tika-parsers-standard-package"),
                "broad Tika standard parser package pulls review-required transitive licenses"
        );
    }

    private static Set<String> declaredDependencies() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        var document = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        var dependencyNodes = document.getElementsByTagName("dependency");
        Set<String> dependencies = new HashSet<>();
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependency = (Element) dependencyNodes.item(i);
            dependencies.add(textOf(dependency, "groupId") + ":" + textOf(dependency, "artifactId"));
        }
        return dependencies;
    }

    private static String textOf(Element dependency, String tagName) {
        var nodes = dependency.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().strip();
    }
}

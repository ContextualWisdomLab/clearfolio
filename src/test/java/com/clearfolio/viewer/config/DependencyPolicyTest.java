package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class DependencyPolicyTest {

    @Test
    void pomDoesNotDeclareBroadTikaParserPackageForBuyerRelease() throws Exception {
        Map<String, DependencyDeclaration> dependencies = declaredDependencies();

        assertFalse(
                dependencies.containsKey("org.apache.tika:tika-parsers-standard-package"),
                "broad Tika standard parser package pulls review-required transitive licenses"
        );
    }

    @Test
    void pomUsesBuyerReleaseFriendlyRuntimeDependencyPolicy() throws Exception {
        Map<String, DependencyDeclaration> dependencies = declaredDependencies();

        assertTrue(
                dependencies.containsKey("org.springframework.boot:spring-boot-starter-log4j2"),
                "Log4j2 starter keeps runtime logging on Apache-licensed components"
        );
        assertSpringStarterExcludes(dependencies, "org.springframework.boot:spring-boot-starter-webflux");
        assertSpringStarterExcludes(dependencies, "org.springframework.boot:spring-boot-starter-validation");
    }

    private static void assertSpringStarterExcludes(
            Map<String, DependencyDeclaration> dependencies,
            String coordinate
    ) {
        DependencyDeclaration dependency = dependencies.get(coordinate);

        assertTrue(
                dependencies.containsKey(coordinate),
                coordinate + " must remain declared so buyer-release exclusions stay explicit"
        );
        assertTrue(
                dependency.excludes("org.springframework.boot:spring-boot-starter-logging"),
                coordinate + " must exclude the default Logback logging starter"
        );
        assertTrue(
                dependency.excludes("jakarta.annotation:jakarta.annotation-api"),
                coordinate + " must exclude the annotation API until legal clears GPL classpath-exception metadata"
        );
    }

    private static Map<String, DependencyDeclaration> declaredDependencies() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        var document = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        var dependencyNodes = document.getElementsByTagName("dependency");
        Map<String, DependencyDeclaration> dependencies = new TreeMap<>();
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependency = (Element) dependencyNodes.item(i);
            DependencyDeclaration declaration = new DependencyDeclaration(
                    directChildTextOf(dependency, "groupId"),
                    directChildTextOf(dependency, "artifactId"),
                    exclusionsOf(dependency)
            );
            dependencies.put(declaration.coordinate(), declaration);
        }
        return dependencies;
    }

    private static Set<String> exclusionsOf(Element dependency) {
        var exclusionNodes = dependency.getElementsByTagName("exclusion");
        Set<String> exclusions = new HashSet<>();
        for (int i = 0; i < exclusionNodes.getLength(); i++) {
            Element exclusion = (Element) exclusionNodes.item(i);
            exclusions.add(directChildTextOf(exclusion, "groupId") + ":" + directChildTextOf(exclusion, "artifactId"));
        }
        return exclusions;
    }

    private static String directChildTextOf(Element dependency, String tagName) {
        for (Node node = dependency.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent().strip();
            }
        }
        return "";
    }

    private record DependencyDeclaration(String groupId, String artifactId, Set<String> exclusions) {
        private String coordinate() {
            return groupId + ":" + artifactId;
        }

        private boolean excludes(String coordinate) {
            return exclusions.contains(coordinate);
        }
    }
}

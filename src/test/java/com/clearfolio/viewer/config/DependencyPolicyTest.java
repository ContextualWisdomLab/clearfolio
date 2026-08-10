package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
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

    @Test
    void pomPinsPatchedNettyLineForReactiveHttpServing() throws Exception {
        Document document = parsedPom();
        Element properties = (Element) document.getElementsByTagName("properties").item(0);

        assertEquals(
                "4.1.136.Final",
                directChildTextOf(properties, "netty.version"),
                "Spring Boot's managed Netty line must be overridden to the reviewed 4.1.136.Final security release"
        );
    }

    @Test
    void mavenVerifyGeneratesWarningFreePublicApiJavadocs() throws Exception {
        Document document = parsedPom();
        Element properties = (Element) document.getElementsByTagName("properties").item(0);

        assertEquals(
                "3.12.0",
                directChildTextOf(properties, "maven.javadoc.version"),
                "the warning-free public Javadoc gate must use the reviewed Maven Javadoc Plugin release"
        );

        Element plugin = buildPlugin(
                document,
                "org.apache.maven.plugins",
                "maven-javadoc-plugin"
        );
        assertTrue(
                plugin != null,
                "mvn verify must include the Maven Javadoc Plugin instead of relying on undocumented local checks"
        );
        assertEquals(
                "${maven.javadoc.version}",
                directChildTextOf(plugin, "version"),
                "the Javadoc plugin version must be controlled by the authoritative version property"
        );

        Element configuration = directChildElementOf(plugin, "configuration");
        assertTrue(configuration != null, "the Javadoc plugin must declare fail-closed configuration");
        assertEquals("all", directChildTextOf(configuration, "doclint"));
        assertEquals("true", directChildTextOf(configuration, "failOnError"));
        assertEquals("true", directChildTextOf(configuration, "failOnWarnings"));
        assertEquals("public", directChildTextOf(configuration, "show"));

        Element execution = executionById(plugin, "validate-public-api-documentation");
        assertTrue(
                execution != null,
                "the public Javadoc acceptance gate must have a stable execution identifier"
        );
        assertEquals(
                "verify",
                directChildTextOf(execution, "phase"),
                "public Javadoc validation must run in the authoritative Maven verify lifecycle"
        );
        assertTrue(
                directChildTextsOf(directChildElementOf(execution, "goals"), "goal")
                        .contains("javadoc"),
                "the verify-bound execution must invoke the Javadoc goal"
        );
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
        var dependencyNodes = parsedPom().getElementsByTagName("dependency");
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

    private static Document parsedPom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
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

    private static Element buildPlugin(
            Document document,
            String groupId,
            String artifactId
    ) {
        var pluginNodes = document.getElementsByTagName("plugin");
        for (int index = 0; index < pluginNodes.getLength(); index++) {
            Element plugin = (Element) pluginNodes.item(index);
            if (groupId.equals(directChildTextOf(plugin, "groupId"))
                    && artifactId.equals(directChildTextOf(plugin, "artifactId"))) {
                return plugin;
            }
        }
        return null;
    }

    private static Element executionById(Element plugin, String executionId) {
        Element executions = directChildElementOf(plugin, "executions");
        if (executions == null) {
            return null;
        }
        for (Node node = executions.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element execution
                    && "execution".equals(execution.getTagName())
                    && executionId.equals(directChildTextOf(execution, "id"))) {
                return execution;
            }
        }
        return null;
    }

    private static Set<String> directChildTextsOf(Element parent, String tagName) {
        Set<String> values = new HashSet<>();
        if (parent == null) {
            return values;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                values.add(element.getTextContent().strip());
            }
        }
        return values;
    }

    private static Element directChildElementOf(Element parent, String tagName) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static String directChildTextOf(Element dependency, String tagName) {
        Element child = directChildElementOf(dependency, tagName);
        return child == null ? "" : child.getTextContent().strip();
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

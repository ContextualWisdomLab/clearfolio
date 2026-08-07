package com.clearfolio.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Clearfolio Viewer Spring Boot application.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ClearfolioViewerApplication {

    /**
     * Creates the Spring configuration instance used during application startup.
     */
    public ClearfolioViewerApplication() {
        // Spring instantiates the application configuration class while building the context.
    }

    /**
     * Boots the application with the provided command line arguments.
     *
     * @param args command line arguments passed at startup
     */
    public static void main(String[] args) {
        SpringApplication.run(ClearfolioViewerApplication.class, args);
    }
}

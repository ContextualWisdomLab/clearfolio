package com.clearfolio.viewer.config;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Adds global security headers to all responses.
 */
@Component
public final class GlobalSecurityHeadersWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            final ServerWebExchange exchange,
            final WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // Set nosniff globally for all responses (API and Viewer)
            if (!headers.containsKey("X-Content-Type-Options")) {
                headers.set("X-Content-Type-Options", "nosniff");
            }

            // Set X-Frame-Options to DENY by default for APIs
            if (!isViewerSurface(exchange.getRequest().getPath().value())) {
                if (!headers.containsKey("X-Frame-Options")) {
                    headers.set("X-Frame-Options", "DENY");
                }
            }

            // Set HSTS header
            if (!headers.containsKey("Strict-Transport-Security")) {
                headers.set(
                        "Strict-Transport-Security",
                        "max-age=31536000 ; includeSubDomains"
                );
            }

            // Set Cache-Control for APIs to prevent caching of sensitive data
            if (isApiSurface(exchange.getRequest().getPath().value())) {
                if (!headers.containsKey(HttpHeaders.CACHE_CONTROL)) {
                    headers.set(
                            HttpHeaders.CACHE_CONTROL,
                            "no-store, max-age=0"
                    );
                }
            }

            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    private boolean isViewerSurface(final String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.equals("/viewer") || path.startsWith("/viewer/")) {
            return true;
        }
        return false;
    }

    private boolean isApiSurface(final String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.startsWith("/api/");
    }
}

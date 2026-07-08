package com.clearfolio.viewer.config;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Adds browser security headers for the viewer HTML surface.
 *
 * <p>In this repo, most endpoints are JSON APIs. The viewer path is special:
 * it is intended to be embedded by downstream platforms and will eventually
 * host a PDF.js-powered UI shell. Headers here focus on clickjacking defenses
 * (CSP frame-ancestors) and a CSP baseline that is compatible with PDF.js
 * workers (worker-src blob:).
 */
@Component
public final class ViewerSecurityHeadersWebFilter implements WebFilter {

    /** Configured frame-ancestors. */
    private final String frameAncestors;

    /**
     * Creates filter.
     *
     * @param frameAncestorsValue The configured frame-ancestors value
     */
    public ViewerSecurityHeadersWebFilter(
            @Value("${viewer.security.frame-ancestors:self}")
            final String frameAncestorsValue) {
        this.frameAncestors = normalizeFrameAncestors(frameAncestorsValue);
    }

    @Override
    public Mono<Void> filter(
            final ServerWebExchange exchange,
            final WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!isViewerSurface(path)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // Avoid caching embedded preview surfaces.
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");

            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("X-XSS-Protection", "0");
            headers.set("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains");
            if ("'self'".equals(frameAncestors)) {
                headers.set("X-Frame-Options", "SAMEORIGIN");
            } else if ("'none'".equals(frameAncestors)) {
                headers.set("X-Frame-Options", "DENY");
            }

            // If the response is a redirect, do not attach an error-like CSP
            // that could confuse debugging.
            if (exchange.getResponse().getStatusCode() == HttpStatus.FOUND) {
                return Mono.empty();
            }

            // CSP goal: strict by default, but allow same-origin JS/CSS
            // and PDF.js workers.
            headers.set(
                    "Content-Security-Policy",
                    String.join("; ",
                            "default-src 'none'",
                            "base-uri 'none'",
                            "frame-ancestors " + frameAncestors,
                            "script-src 'self'",
                            "style-src 'self'",
                            "img-src 'self' data: blob:",
                            "font-src 'self' data:",
                            "connect-src 'self'",
                            "worker-src 'self' blob:",
                            "frame-src 'self' blob:",
                            "object-src 'none'"
                    )
            );

            return Mono.empty();
        });

        // Ensure CSP is also applied to HEAD checks for the viewer surface.
        if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
            return chain.filter(exchange).then(Mono.empty());
        }

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

    /** List of allowed domains. */
    private static final java.util.List<String> ALLOWED_DOMAINS =
            java.util.Arrays.asList("'self'", "'none'", "https://example.test");

    private String normalizeFrameAncestors(final String configured) {
        if (configured == null) {
            return "'self'";
        }
        final String trimmed = configured.trim();
        if (trimmed.isEmpty() || Objects.equals(trimmed, "self")) {
            return "'self'";
        }

        final String[] origins = trimmed.split("\s+");
        for (final String origin : origins) {
            if (!ALLOWED_DOMAINS.contains(origin)) {
                return "'self'";
            }
        }

        return trimmed;
    }
}

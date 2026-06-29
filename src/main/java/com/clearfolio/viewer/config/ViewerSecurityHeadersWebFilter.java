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
 * Adds browser security headers for the application.
 *
 * <p>It applies strict baseline security headers to all endpoints (APIs and viewer).
 * The viewer path is special: it is intended to be embedded by downstream platforms
 * and will eventually host a PDF.js-powered UI shell. Therefore, its CSP is slightly
 * more relaxed (worker-src blob:) and its frame-ancestors is configurable.
 * For non-viewer APIs, clickjacking is completely blocked and strict CSP is applied.
 */
@Component
public class ViewerSecurityHeadersWebFilter implements WebFilter {

    private final String frameAncestors;

    public ViewerSecurityHeadersWebFilter(
            @Value("${viewer.security.frame-ancestors:self}") String frameAncestors) {
        this.frameAncestors = normalizeFrameAncestors(frameAncestors);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean isViewer = isViewerSurface(path);

        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // Baseline security headers for all endpoints
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("X-XSS-Protection", "1; mode=block");
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

            // If the response is a redirect, do not attach an error-like CSP that could confuse debugging.
            if (exchange.getResponse().getStatusCode() == HttpStatus.FOUND) {
                return Mono.empty();
            }

            if (isViewer) {
                // CSP goal: strict by default, but allow same-origin JS/CSS and PDF.js workers.
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
            } else {
                // API endpoints: completely deny framing and restrict content
                headers.set("X-Frame-Options", "DENY");
                headers.set(
                        "Content-Security-Policy",
                        String.join("; ",
                                "default-src 'none'",
                                "frame-ancestors 'none'",
                                "base-uri 'none'",
                                "form-action 'none'"
                        )
                );
            }

            return Mono.empty();
        });

        // Ensure headers are applied to HEAD checks as well.
        if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
            return chain.filter(exchange).then(Mono.empty());
        }

        return chain.filter(exchange);
    }

    private boolean isViewerSurface(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.equals("/viewer") || path.startsWith("/viewer/")) {
            return true;
        }
        return false;
    }

    private String normalizeFrameAncestors(String configured) {
        if (configured == null) {
            return "'self'";
        }
        String trimmed = configured.trim();
        if (trimmed.isEmpty()) {
            return "'self'";
        }
        if (Objects.equals(trimmed, "self")) {
            return "'self'";
        }
        return trimmed;
    }
}

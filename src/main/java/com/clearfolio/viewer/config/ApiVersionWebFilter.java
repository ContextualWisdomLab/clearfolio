package com.clearfolio.viewer.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Publishes the current HTTP API version and rejects explicitly unsupported
 * client version requests before controller dispatch.
 *
 * <p>Clearfolio's shipped public routes are currently rooted at {@code /api/v1}.
 * Existing clients that do not send a version header therefore continue to use
 * that path-selected contract. Clients that do send the version header must
 * request the exact current version; unsupported values fail closed with a
 * controlled error envelope instead of being silently interpreted as v1.
 */
@Component
public final class ApiVersionWebFilter implements WebFilter {

    /**
     * Response/request header used for explicit API contract negotiation.
     */
    public static final String VERSION_HEADER = "X-Clearfolio-Api-Version";

    /**
     * Version identifier for the currently shipped {@code /api/v1} contract.
     */
    public static final String CURRENT_VERSION = "v1";

    /**
     * Creates the stateless API-version negotiation filter.
     */
    public ApiVersionWebFilter() {
        // No mutable state is required for exact version negotiation.
    }

    /**
     * Applies version negotiation to public API routes while leaving non-API
     * surfaces unchanged.
     *
     * @param exchange current reactive HTTP exchange
     * @param chain remaining WebFlux filter chain
     * @return completion signal for the filtered request
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        exchange.getResponse().getHeaders().set(VERSION_HEADER, CURRENT_VERSION);
        String requestedVersion = exchange.getRequest().getHeaders().getFirst(VERSION_HEADER);
        if (requestedVersion == null
                || requestedVersion.isBlank()
                || CURRENT_VERSION.equals(requestedVersion)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = unsupportedVersionBody();
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private byte[] unsupportedVersionBody() {
        String body = ("{\"errorCode\":\"UNSUPPORTED_API_VERSION\","
                + "\"message\":\"Unsupported API version\","
                + "\"traceId\":\"%s\","
                + "\"details\":{\"supportedVersion\":\"v1\"},"
                + "\"code\":\"UNSUPPORTED_API_VERSION\"}")
                .formatted(UUID.randomUUID());
        return body.getBytes(StandardCharsets.UTF_8);
    }
}

package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

class GlobalSecurityHeadersWebFilterTest {

    @Test
    void addsSecurityHeadersForApiSurface() {
        GlobalSecurityHeadersWebFilter filter = new GlobalSecurityHeadersWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/convert/jobs/123").build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("max-age=31536000 ; includeSubDomains", headers.getFirst("Strict-Transport-Security"));
        assertEquals("no-store, max-age=0", headers.getFirst(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void doesNotAddFrameOptionsForViewerSurface() {
        GlobalSecurityHeadersWebFilter filter = new GlobalSecurityHeadersWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/viewer/index.html").build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertNull(headers.getFirst("X-Frame-Options")); // Handled by ViewerSecurityHeadersWebFilter (CSP frame-ancestors)
        assertEquals("max-age=31536000 ; includeSubDomains", headers.getFirst("Strict-Transport-Security"));
        assertNull(headers.getFirst(HttpHeaders.CACHE_CONTROL)); // Handled by ViewerSecurityHeadersWebFilter
    }

    @Test
    void doesNotAddFrameOptionsForViewerExactSurface() {
        GlobalSecurityHeadersWebFilter filter = new GlobalSecurityHeadersWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/viewer").build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertNull(headers.getFirst("X-Frame-Options")); // Handled by ViewerSecurityHeadersWebFilter (CSP frame-ancestors)
        assertEquals("max-age=31536000 ; includeSubDomains", headers.getFirst("Strict-Transport-Security"));
        assertNull(headers.getFirst(HttpHeaders.CACHE_CONTROL)); // Handled by ViewerSecurityHeadersWebFilter
    }

    @Test
    void addsPartialHeadersForOtherSurfaces() {
        GlobalSecurityHeadersWebFilter filter = new GlobalSecurityHeadersWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healthz").build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("max-age=31536000 ; includeSubDomains", headers.getFirst("Strict-Transport-Security"));
        assertNull(headers.getFirst(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void doesNotOverwriteExistingHeaders() {
        GlobalSecurityHeadersWebFilter filter = new GlobalSecurityHeadersWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/convert/jobs/123").build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            webExchange.getResponse().getHeaders().set("X-Content-Type-Options", "already-set");
            webExchange.getResponse().getHeaders().set("X-Frame-Options", "SAMEORIGIN");
            webExchange.getResponse().getHeaders().set("Strict-Transport-Security", "max-age=123");
            webExchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("already-set", headers.getFirst("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", headers.getFirst("X-Frame-Options"));
        assertEquals("max-age=123", headers.getFirst("Strict-Transport-Security"));
        assertEquals("public, max-age=3600", headers.getFirst(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void doesNotOverwriteExistingHeadersForOtherSurfaces() {
        GlobalSecurityHeadersWebFilter filter = new GlobalSecurityHeadersWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healthz").build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            webExchange.getResponse().getHeaders().set("X-Content-Type-Options", "already-set");
            webExchange.getResponse().getHeaders().set("X-Frame-Options", "SAMEORIGIN");
            webExchange.getResponse().getHeaders().set("Strict-Transport-Security", "max-age=123");
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("already-set", headers.getFirst("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", headers.getFirst("X-Frame-Options"));
        assertEquals("max-age=123", headers.getFirst("Strict-Transport-Security"));
    }

    @Test
    void treatsBlankApiSurfaceAsNonApiSurface() {
        GlobalSecurityHeadersWebFilter filter = new GlobalSecurityHeadersWebFilter();
        AtomicBoolean invoked = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            invoked.set(true);
            return Mono.empty();
        };

        ServerWebExchange blankPath = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath path = mock(RequestPath.class);
        when(blankPath.getRequest()).thenReturn(request);
        when(request.getPath()).thenReturn(path);
        when(path.value()).thenReturn("   ");
        when(blankPath.getResponse()).thenReturn(new MockServerHttpResponse());

        filter.filter(blankPath, chain).block();
        assertTrue(invoked.get());
    }

    @Test
    void treatsNullApiSurfaceAsNonApiSurface() {
        GlobalSecurityHeadersWebFilter filter = new GlobalSecurityHeadersWebFilter();
        AtomicBoolean invoked = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            invoked.set(true);
            return Mono.empty();
        };
        ServerWebExchange nullPath = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath path = mock(RequestPath.class);
        when(nullPath.getRequest()).thenReturn(request);
        when(request.getPath()).thenReturn(path);
        when(path.value()).thenReturn(null);
        when(nullPath.getResponse()).thenReturn(new MockServerHttpResponse());

        filter.filter(nullPath, chain).block();
        assertTrue(invoked.get());
    }
}

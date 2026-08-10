package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.clearfolio.viewer.api.ApiErrorResponse;

/**
 * Verifies that client-controlled trace identifiers cannot become unbounded or
 * path-like diagnostic identifiers while preserving a bounded opaque value.
 */
class ApiExceptionHandlerTraceIdValidationTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unsafeClientTraceIdFallsBackToServerRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header("X-Trace-Id", "../../tenant-secret")
                        .build()
        );

        ApiErrorResponse body = handler
                .handleBadRequest(new IllegalArgumentException("bad request"), exchange)
                .getBody();

        assertNotEquals("../../tenant-secret", body.traceId());
        assertEquals(exchange.getRequest().getId(), body.traceId());
    }

    @Test
    void unsafeClientAndServerTraceIdsFallBackToGeneratedUuid() {
        String unsafeClientTraceId = "../../tenant-secret";
        String unsafeRequestId = "../unsafe-server-request";
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Trace-Id", unsafeClientTraceId);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getId()).thenReturn(unsafeRequestId);

        ApiErrorResponse body = handler
                .handleBadRequest(new IllegalArgumentException("bad request"), exchange)
                .getBody();

        assertNotEquals(unsafeClientTraceId, body.traceId());
        assertNotEquals(unsafeRequestId, body.traceId());
        assertEquals(UUID.fromString(body.traceId()).toString(), body.traceId());
    }

    @Test
    void oversizedClientTraceIdFallsBackToServerRequestId() {
        String oversized = "a".repeat(129);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header("X-Trace-Id", oversized)
                        .build()
        );

        ApiErrorResponse body = handler
                .handleBadRequest(new IllegalArgumentException("bad request"), exchange)
                .getBody();

        assertNotEquals(oversized, body.traceId());
        assertEquals(exchange.getRequest().getId(), body.traceId());
    }

    @Test
    void boundedOpaqueClientTraceIdIsPreserved() {
        String traceId = "req-20260811_02:17.55";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header("X-Trace-Id", traceId)
                        .build()
        );

        ApiErrorResponse body = handler
                .handleBadRequest(new IllegalArgumentException("bad request"), exchange)
                .getBody();

        assertEquals(traceId, body.traceId());
    }
}

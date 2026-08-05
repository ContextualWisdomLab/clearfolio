package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ServerWebExchange;

import com.clearfolio.viewer.api.ApiErrorResponse;

/**
 * Verifies security-sensitive boundary behavior in {@link ApiExceptionHandler}.
 */
class ApiExceptionHandlerCoverageTest {

    @Test
    void logSanitizationReplacesBlockedCharactersAndPreservesTheNextCodePoint() throws Exception {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        Method method = ApiExceptionHandler.class.getDeclaredMethod("sanitizeForLog", String.class);
        method.setAccessible(true);

        assertEquals("\u202F", (String) method.invoke(handler, "\u202F"));
        assertEquals("_", (String) method.invoke(handler, "\u202E"));
        assertEquals("__", (String) method.invoke(handler, "\r\n"));
    }

    @Test
    void typeMismatchRedactsTheRejectedExternalValue() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException(
                "customer-secret-value",
                Boolean.class,
                "deadLettered",
                null,
                new IllegalArgumentException("bad boolean")
        );
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getId()).thenReturn("request-redaction");

        ResponseEntity<ApiErrorResponse> response = handler.handleTypeMismatch(mismatch, exchange);

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("deadLettered", body.details().get("parameter"));
        assertEquals("[redacted]", body.details().get("value"));
    }
}

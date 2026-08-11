package com.clearfolio.viewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

class ApiVersionWebFilterTest {

    @Test
    void nonApiRequestsBypassVersionNegotiation() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/healthz"));
        AtomicBoolean invoked = new AtomicBoolean();
        WebFilterChain chain = current -> {
            invoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(invoked).isTrue();
        assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(ApiVersionWebFilter.VERSION_HEADER);
    }

    @Test
    void missingVersionHeaderUsesCurrentV1Contract() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/convert/jobs"));
        AtomicBoolean invoked = new AtomicBoolean();

        filter.filter(exchange, current -> {
            invoked.set(true);
            return Mono.empty();
        }).block();

        assertThat(invoked).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiVersionWebFilter.VERSION_HEADER))
                .isEqualTo(ApiVersionWebFilter.CURRENT_VERSION);
    }

    @Test
    void explicitCurrentVersionIsAcceptedWithoutChangingRouting() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/viewer/00000000-0000-0000-0000-000000000000")
                        .header(ApiVersionWebFilter.VERSION_HEADER, ApiVersionWebFilter.CURRENT_VERSION)
        );
        AtomicBoolean invoked = new AtomicBoolean();

        filter.filter(exchange, current -> {
            invoked.set(true);
            return Mono.empty();
        }).block();

        assertThat(invoked).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiVersionWebFilter.VERSION_HEADER))
                .isEqualTo(ApiVersionWebFilter.CURRENT_VERSION);
    }

    @Test
    void blankVersionHeaderPreservesBackwardCompatibleV1Default() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/convert/jobs")
                        .header(ApiVersionWebFilter.VERSION_HEADER, "   ")
        );
        AtomicBoolean invoked = new AtomicBoolean();

        filter.filter(exchange, current -> {
            invoked.set(true);
            return Mono.empty();
        }).block();

        assertThat(invoked).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiVersionWebFilter.VERSION_HEADER))
                .isEqualTo(ApiVersionWebFilter.CURRENT_VERSION);
    }

    @Test
    void unsupportedVersionFailsClosedBeforeControllerDispatch() {
        ApiVersionWebFilter filter = new ApiVersionWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/convert/jobs")
                        .header(ApiVersionWebFilter.VERSION_HEADER, "v2")
        );
        AtomicBoolean invoked = new AtomicBoolean();

        filter.filter(exchange, current -> {
            invoked.set(true);
            return Mono.empty();
        }).block();

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiVersionWebFilter.VERSION_HEADER))
                .isEqualTo(ApiVersionWebFilter.CURRENT_VERSION);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString()).isEqualTo("application/json");
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body)
                .contains("\"errorCode\":\"UNSUPPORTED_API_VERSION\"")
                .contains("\"code\":\"UNSUPPORTED_API_VERSION\"")
                .contains("\"supportedVersion\":\"v1\"")
                .doesNotContain("v2");
    }
}

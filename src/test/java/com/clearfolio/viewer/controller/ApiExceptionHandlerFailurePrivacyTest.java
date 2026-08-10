package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * Verifies that unexpected-error diagnostics retain a controlled failure class
 * without logging provider-controlled exception messages.
 */
class ApiExceptionHandlerFailurePrivacyTest {

    @Test
    void unexpectedErrorLogExcludesRawExceptionMessageButKeepsFailureClass() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Trace-Id", "trace-safe-42");
        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getId()).thenReturn("request-safe-42");
        when(request.getURI()).thenReturn(URI.create("https://example.test/api/v1/test"));

        String providerMessage = "customer@example.com /tenant/private/report.pdf";
        CapturingAppender appender = attachAppender();
        try {
            handler.handleUnexpected(new RuntimeException(providerMessage), exchange);
        } finally {
            appender.closeAndDetach();
        }

        String renderedLog = appender.renderedLog();
        assertFalse(renderedLog.contains(providerMessage));
        assertFalse(renderedLog.contains("customer@example.com"));
        assertFalse(renderedLog.contains("/tenant/private/report.pdf"));
        assertTrue(renderedLog.contains("RuntimeException"));
        assertTrue(renderedLog.contains("trace-safe-42"));
    }

    private static CapturingAppender attachAppender() {
        Logger logger = (Logger) LogManager.getLogger(ApiExceptionHandler.class);
        CapturingAppender appender = new CapturingAppender(logger);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ERROR);
        return appender;
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final Logger logger;
        private final List<String> renderedEvents = new ArrayList<>();

        private CapturingAppender(Logger logger) {
            super(
                    "api-exception-handler-failure-privacy-test",
                    null,
                    PatternLayout.newBuilder().withPattern("%m%throwable").build(),
                    false,
                    null
            );
            this.logger = logger;
        }

        @Override
        public void append(LogEvent event) {
            renderedEvents.add(getLayout().toSerializable(event).toString());
        }

        private String renderedLog() {
            return String.join("\n", renderedEvents);
        }

        private void closeAndDetach() {
            logger.removeAppender(this);
            stop();
        }
    }
}

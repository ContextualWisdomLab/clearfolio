package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PolicyOverrideRequestTest {

    @Test
    void noneReturnsSharedEmptyInstance() {
        PolicyOverrideRequest none = PolicyOverrideRequest.none();

        assertNotNull(none);
        assertNull(none.policyOverride());
        assertNull(none.approvalToken());
        assertNull(none.approverId());
        assertSame(none, PolicyOverrideRequest.of(null, null, null));
    }

    @Test
    void ofRetainsProvidedHeaderValues() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of("true", "token-123", "approver-1");

        assertEquals("true", request.policyOverride());
        assertEquals("token-123", request.approvalToken());
        assertEquals("approver-1", request.approverId());
    }

    @Test
    void ofCreatesDistinctInstanceWhenOnlyPartialHeadersArePresent() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of(null, "token-123", null);

        assertNotSame(PolicyOverrideRequest.none(), request);
        assertNull(request.policyOverride());
        assertEquals("token-123", request.approvalToken());
        assertNull(request.approverId());
    }

    @Test
    void ofCreatesDistinctInstanceWhenOnlyApproverHeaderIsPresent() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of(null, null, "approver-1");

        assertNotSame(PolicyOverrideRequest.none(), request);
        assertNull(request.policyOverride());
        assertNull(request.approvalToken());
        assertEquals("approver-1", request.approverId());
    }

    @Test
    void toStringRedactsApprovalTokenAndApproverIdentifier() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of(
                "true",
                "secret-token",
                "private-approver@example.com"
        );

        String rendered = request.toString();

        assertTrue(rendered.contains("approvalToken='[redacted]'"));
        assertTrue(rendered.contains("approverId='[redacted]'"));
        assertFalse(rendered.contains("secret-token"));
        assertFalse(rendered.contains("private-approver@example.com"));
    }

    @Test
    void toStringNormalizesControlCharactersInPrintableOverrideFlag() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of(
                "tr\r\nue\t",
                "secret-token",
                "sensitive-user\r\n1\t"
        );

        String rendered = request.toString();

        assertTrue(rendered.contains("policyOverride='tr__ue_'"));
        assertTrue(rendered.contains("approverId='[redacted]'"));
        assertFalse(rendered.contains("sensitive-user"));
    }

    @Test
    void toStringHandlesNullPrintableHeaderWithoutRevealingIdentityState() {
        String rendered = PolicyOverrideRequest.none().toString();

        assertTrue(rendered.contains("policyOverride='null'"));
        assertTrue(rendered.contains("approverId='[redacted]'"));
    }
}

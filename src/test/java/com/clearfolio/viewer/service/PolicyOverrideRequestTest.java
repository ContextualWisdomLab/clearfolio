package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        PolicyOverrideRequest request = PolicyOverrideRequest.of("true", "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", "approver-1");

        assertEquals("true", request.policyOverride());
        assertEquals("03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", request.approvalToken());
        assertEquals("approver-1", request.approverId());
    }

    @Test
    void ofCreatesDistinctInstanceWhenOnlyPartialHeadersArePresent() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of(null, "03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", null);

        assertNotSame(PolicyOverrideRequest.none(), request);
        assertNull(request.policyOverride());
        assertEquals("03348086ff55b33f2e1e88ff1ccdd4afe499ac911b20594200a96b5b7335831a5e7ceee6dffc71d2439b6851efc1e05b1ecec2390870d3029a54632e366257432918e7fd81809778a8b09dcaa66e787bc15597060b7768ffad79ae40cdde6c537b7481cc3c929abd584511931d8d5b9d8f2a7792e5a8599a30e674c140adea44c30ff170117abeaf59499e651497ec1a990207fe4cdb54998d76669c9039ef1ad16d0d8f802fb08b4ae83f87219d8fbc70ad08318912c10f23b44d8ea0fec0e3ff113b9803e0921833447116b965bd07150eff0c5e2fade97e43864b19dfb8a0b64d2912d20adb3eae7811ff42812e0375015954eb561d74bed42ddd653abf44", request.approvalToken());
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
    void toStringRedactsApprovalToken() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of("true", "secret-token", "approver-1");

        String rendered = request.toString();

        assertTrue(rendered.contains("approvalToken='[redacted]'"));
        assertFalse(rendered.contains("secret-token"));
    }

    @Test
    void toStringNormalizesControlCharactersInPrintableHeaders() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of("true\n", "secret-token", "approver\r\n1\t");

        String rendered = request.toString();

        assertTrue(rendered.contains("policyOverride='true_'"));
        assertTrue(rendered.contains("approverId='approver__1_'"));
    }

    @Test
    void toStringHandlesNullPrintableHeaders() {
        String rendered = PolicyOverrideRequest.none().toString();

        assertTrue(rendered.contains("policyOverride='null'"));
        assertTrue(rendered.contains("approverId='null'"));
    }
}

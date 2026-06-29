package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.clearfolio.viewer.service.PolicyOverrideRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"conversion.policy-override-public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3xHXr62epKU0uN+XCGQcIdLY4HKCgRQcX8VOy+08u5W0lZr18phNlAn0leEzYKXERADPDTeKA6TpZZ2f31uZgEggN/+tBGVV8zVtj4v8StmOOTHKTSuqEsy0lyHw54fhZRl3f4blkdrruag3m/pAC5/5y+lH7aHPOE5HH2k89itiIlElZv4U03wP/EwPgHIr8WhYxbRWQXKa6es0Dll1aFzlgCwmBaXdhRHn6N5cv1SX5vIA6qSlxEMyk2seCUayIe8L1svLOhHlYT/KnpwvLatHLe8TaF/lWbwwb0hu7ZJcggXtBuxJapWk08eFTTSUYHEREOqZaIF5H0z6F2tB4QIDAQAB"})
@AutoConfigureWebTestClient
@TestPropertySource(
        properties = {
                "conversion.max-upload-size-bytes=1024",
                "spring.codec.max-in-memory-size=2048"
        }
)
class ConversionControllerMultipartLimitTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void submitReturnsBadRequestWhenUploadExceedsReactiveCodecLimit() {
        byte[] payload = new byte[2049];

        submit("report.docx", payload)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo("File is too large.")
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitReturnsBadRequestWhenFilenameIsMissingExtension() {
        submit("report", "hello".getBytes())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(value -> assertContains((String) value, "File extension is required"))
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitReturnsUnsupportedFormatForBlockedExtensionWithServiceValidation() {
        submit("contract.hwp", "hello".getBytes())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNSUPPORTED_FORMAT")
                .jsonPath("$.code").isEqualTo("UNSUPPORTED_FORMAT")
                .jsonPath("$.details.extension").isEqualTo("hwp")
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitAcceptsBlockedExtensionWhenPolicyOverrideHeadersAreValid() {
        submit("contract.hwp", "hello".getBytes(), "true", "355e15c74787051642f810d47f9d85959929363eb16f06078d25cad121acaf7d2074205a53ef638bbec09ac4873a331b99c08a66f224c7fad138fe7d6fb600c81e4958caed6d4b64138cba912df801dd0780b59b3b00f47648575a5d724a6f9a78bbdede44ae9a660b263b672fd595c9679aca792c1e5b1ef1ef1284d2d0e59c43994b6ecd939b0ff50eade736f85adf1f4eda38e153e7efd483a55fdee9323359a65d783f912971169572a7f68a3bab74b15536d85a9d1c79582b6af16d1cdb4caeb44ed5392cfb4162af107146e692621ffaaa63295a3133e93fe56006d57b09cbce8dcad3a67f160cef9156a916ad5f02781b17195e2a2c190078309b1d06", "approver-99")
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.jobId").value(value -> assertContains((String) value, "-"));
    }

    @Test
    void submitRejectsBlockedExtensionWhenOverrideTokenIsMissing() {
        submit("contract.hwp", "hello".getBytes(), "true", " ", "approver-99")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo(
                        "X-Clearfolio-Approval-Token is required when policy override is true.")
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitRejectsBlockedExtensionWhenOverrideApproverIsMissing() {
        submit("contract.hwp", "hello".getBytes(), "true", "355e15c74787051642f810d47f9d85959929363eb16f06078d25cad121acaf7d2074205a53ef638bbec09ac4873a331b99c08a66f224c7fad138fe7d6fb600c81e4958caed6d4b64138cba912df801dd0780b59b3b00f47648575a5d724a6f9a78bbdede44ae9a660b263b672fd595c9679aca792c1e5b1ef1ef1284d2d0e59c43994b6ecd939b0ff50eade736f85adf1f4eda38e153e7efd483a55fdee9323359a65d783f912971169572a7f68a3bab74b15536d85a9d1c79582b6af16d1cdb4caeb44ed5392cfb4162af107146e692621ffaaa63295a3133e93fe56006d57b09cbce8dcad3a67f160cef9156a916ad5f02781b17195e2a2c190078309b1d06", " ")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo(
                        "X-Clearfolio-Approver-Id is required when policy override is true.")
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitRejectsBlockedExtensionWhenOverrideFlagIsInvalid() {
        submit("contract.hwp", "hello".getBytes(), "maybe", "355e15c74787051642f810d47f9d85959929363eb16f06078d25cad121acaf7d2074205a53ef638bbec09ac4873a331b99c08a66f224c7fad138fe7d6fb600c81e4958caed6d4b64138cba912df801dd0780b59b3b00f47648575a5d724a6f9a78bbdede44ae9a660b263b672fd595c9679aca792c1e5b1ef1ef1284d2d0e59c43994b6ecd939b0ff50eade736f85adf1f4eda38e153e7efd483a55fdee9323359a65d783f912971169572a7f68a3bab74b15536d85a9d1c79582b6af16d1cdb4caeb44ed5392cfb4162af107146e692621ffaaa63295a3133e93fe56006d57b09cbce8dcad3a67f160cef9156a916ad5f02781b17195e2a2c190078309b1d06", "approver-99")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo("X-Clearfolio-Policy-Override must be true or false.")
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitReturnsBadRequestWhenServiceUploadLimitIsExceeded() {
        byte[] payload = new byte[1025];

        submit("report.docx", payload)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(value -> assertContains((String) value, "File is too large"))
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitAcceptsDocumentAtServiceUploadLimitBoundary() {
        byte[] payload = new byte[1024];

        submit("report.docx", payload)
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.jobId").value(value -> assertContains((String) value, "-"))
                .jsonPath("$.statusUrl").value(value -> assertContains((String) value, "/api/v1/convert/jobs/"));
    }

    private WebTestClient.ResponseSpec submit(String filename, byte[] content) {
        return submit(filename, content, null, null, null);
    }

    private WebTestClient.ResponseSpec submit(
            String filename,
            byte[] content,
            String policyOverride,
            String approvalToken,
            String approverId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", content)
                .filename(filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        WebTestClient.RequestBodySpec request = webTestClient.post()
                .uri("/api/v1/convert/jobs")
                .contentType(MediaType.MULTIPART_FORM_DATA);
        if (policyOverride != null) {
            request.header(PolicyOverrideRequest.POLICY_OVERRIDE_HEADER, policyOverride);
        }
        if (approvalToken != null) {
            request.header(PolicyOverrideRequest.APPROVAL_TOKEN_HEADER, approvalToken);
        }
        if (approverId != null) {
            request.header(PolicyOverrideRequest.APPROVER_ID_HEADER, approverId);
        }

        return request
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected));
    }

    private static void assertNonBlankTraceId(Object value) {
        String traceId = (String) value;
        assertFalse(traceId.isBlank());
    }
}

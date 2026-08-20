package com.clearfolio.viewer.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionDeliveryReceiptTest {

    @Test
    void preservesExactDeliveryAuthorityAndRecognizesRedelivery() {
        UUID messageId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant firstReceivedAt = Instant.parse("2026-08-11T03:40:00Z");

        ConversionDeliveryReceipt receipt = new ConversionDeliveryReceipt(
                messageId,
                jobId,
                4L,
                firstReceivedAt
        );

        assertEquals(messageId, receipt.messageId());
        assertEquals(jobId, receipt.jobId());
        assertEquals(4L, receipt.generation());
        assertEquals(firstReceivedAt, receipt.firstReceivedAt());
        assertTrue(receipt.authorizes(messageId, jobId, 4L));
        assertFalse(receipt.authorizes(UUID.randomUUID(), jobId, 4L));
        assertFalse(receipt.authorizes(messageId, UUID.randomUUID(), 4L));
        assertFalse(receipt.authorizes(messageId, jobId, 5L));
        assertFalse(receipt.authorizes(null, jobId, 4L));
        assertFalse(receipt.authorizes(messageId, null, 4L));
    }

    @Test
    void rejectsMissingOrInvalidDeliveryAuthority() {
        UUID messageId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant firstReceivedAt = Instant.parse("2026-08-11T03:40:00Z");

        NullPointerException missingMessage = assertThrows(
                NullPointerException.class,
                () -> new ConversionDeliveryReceipt(null, jobId, 1L, firstReceivedAt)
        );
        assertEquals("messageId", missingMessage.getMessage());

        NullPointerException missingJob = assertThrows(
                NullPointerException.class,
                () -> new ConversionDeliveryReceipt(messageId, null, 1L, firstReceivedAt)
        );
        assertEquals("jobId", missingJob.getMessage());

        NullPointerException missingTimestamp = assertThrows(
                NullPointerException.class,
                () -> new ConversionDeliveryReceipt(messageId, jobId, 1L, null)
        );
        assertEquals("firstReceivedAt", missingTimestamp.getMessage());

        IllegalArgumentException zeroGeneration = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionDeliveryReceipt(messageId, jobId, 0L, firstReceivedAt)
        );
        assertEquals("generation must be positive", zeroGeneration.getMessage());

        IllegalArgumentException negativeGeneration = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversionDeliveryReceipt(messageId, jobId, -1L, firstReceivedAt)
        );
        assertEquals("generation must be positive", negativeGeneration.getMessage());
    }
}

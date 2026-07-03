package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

public class DefaultDocumentConversionServiceHexFormatTest {
    @Test
    public void testContentHashHexEncoding() throws Exception {
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
        String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex);
        assertEquals(64, hex.length());
    }
}

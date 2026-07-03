## 2026-07-03 - Hex String Conversion Optimization
**Learning:** String.format("%02x", b) inside a loop creates a massive amount of unnecessary object allocations for byte[] to hex string conversion.
**Action:** Use java.util.HexFormat.of().formatHex(bytes) for much better performance in Java 21, as it is highly optimized for this specific use case.

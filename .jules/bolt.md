## 2026-07-08 - Optimize Byte-to-Hex Conversion
**Learning:** Using `String.format("%02x", b)` in a loop for converting byte arrays to hex strings introduces unnecessary overhead and intermediate string allocations in Java.
**Action:** Use a reusable `java.util.HexFormat` instance (`private static final java.util.HexFormat HEX_FORMAT = java.util.HexFormat.of();`) instead of `String.format` loops or repeated `HexFormat.of()` calls to optimize performance and reduce intermediate allocations.

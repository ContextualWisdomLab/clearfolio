## 2026-06-29 - Use HexFormat for Byte Array to Hex String Conversion
**Learning:** In Java 21, using `String.format("%02x", b)` inside a loop to convert a byte array to a hex string is inefficient and causes unnecessary object allocations and CPU overhead.
**Action:** Always prefer using `java.util.HexFormat.of().formatHex(bytes)` for converting byte arrays to hex strings in this Java 21 codebase to improve performance.

## 2026-07-07 - Optimize Hex String Conversion
**Learning:** In Java 17+, using `String.format(\"%02x\", b)` inside a loop for byte array to hex string conversion is slow and creates many temporary objects. `java.util.HexFormat.of().formatHex(bytes)` is significantly faster and more memory-efficient.
**Action:** Always use `java.util.HexFormat.of().formatHex()` for hex string conversions instead of loops with `String.format` or `Integer.toHexString`.

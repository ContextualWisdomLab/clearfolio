## 2026-06-26 - Optimized Hash String Conversion
**Learning:** In Java, using `String.format("%02x", b)` inside a loop to convert a byte array to a hex string is a significant performance anti-pattern. Benchmarks show it's over 100x slower than modern alternatives.
**Action:** Use `java.util.HexFormat.of().formatHex(raw)` which is highly optimized and avoids intermediate object allocations.

## 2026-07-02 - Java 21 HexFormat vs String.format Performance
**Learning:** In Java 21 codebases, using `String.format("%02x", b)` inside a loop to convert a byte array to a hex string incurs significant memory allocation and execution time overhead.
**Action:** Use `java.util.HexFormat.of().formatHex(bytes)` instead, which is optimized for byte array to hex string conversions without the overhead of manual looping and string formatting allocations.

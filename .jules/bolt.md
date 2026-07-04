## 2026-07-04 - Replace String.format with HexFormat for byte-to-hex conversion
**Learning:** `String.format("%02x", b)` in a loop creates massive GC overhead and string creation bottlenecks in Java. Since this is a Java 17+ codebase, the native `java.util.HexFormat.of().formatHex(byte[])` is available and exponentially faster for hashing algorithms.
**Action:** Always prefer `HexFormat` over `String.format` or manual `StringBuilder` loops when dealing with hex encoding in modern Java environments.

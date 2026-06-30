## 2024-06-30 - Replace String.format with HexFormat for byte-to-hex conversion
**Learning:** Using a loop with `String.format("%02x", b)` to convert byte arrays to hex strings causes unnecessary memory allocations and is a performance bottleneck in this application.
**Action:** Always use `java.util.HexFormat.of().formatHex(bytes)` instead of manual string formatting loops for converting bytes to hex strings to optimize performance and memory usage.

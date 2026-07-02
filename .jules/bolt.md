## 2026-06-30 - Java HexFormat Optimization
**Learning:** In Java 21, `java.util.HexFormat.of().formatHex(bytes)` is highly preferred over iterating and using `String.format(\"%02x\", b)` for converting a byte array to a hex string.
**Action:** Replaced String.format in a loop with HexFormat in DefaultDocumentConversionService to enhance performance during content hash generation.

## 2026-07-08 - Java 17+ Hex String Conversion Optimization
**Learning:** Using String.format("%02x", b) in a loop for byte-to-hex string conversion is a common but slow pattern in older Java code.
**Action:** In Java 17 and later, replace String.format loops with java.util.HexFormat.of().formatHex(bytes) for significantly better performance and memory efficiency without sacrificing readability.

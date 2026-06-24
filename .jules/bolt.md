## 2026-06-24 - [MessageDigest caching]
**Learning:** In Java, creating a MessageDigest instance (`MessageDigest.getInstance("SHA-256")`) is relatively expensive and can be a bottleneck under high concurrent load. It is better to reuse instances, but they are not thread-safe. A `ThreadLocal` or cloning a prototype instance are common workarounds, but reusing instances is best. However, simply using a `MessageDigest` is O(n), no way to skip reading the whole file without changing logic.
**Action:** Optimize `contentHash` method to not recreate `MessageDigest` every time. However, this is micro optimization. Is there something better?

## 2026-06-24 - [String.format to HexFormat optimization]
**Learning:** In Java, using `String.format("%02x", b)` in a loop to convert bytes to hex string is extremely slow due to the overhead of format string parsing and new String allocations. Since Java 17, `java.util.HexFormat` provides a much faster and cleaner way to format bytes to hex strings (approx 100x faster in microbenchmarks). This is highly relevant when hashing files where hex conversion happens on every upload.
**Action:** Replace manual `String.format` loop with `HexFormat.of().formatHex(raw)` in `contentHash` method. This makes file upload faster and reduces garbage collection pressure.

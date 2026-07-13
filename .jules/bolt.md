## 2026-07-08 - Java 17+ Hex String Conversion Optimization
**Learning:** Using String.format("%02x", b) in a loop for byte-to-hex string conversion is a common but slow pattern in older Java code.
**Action:** In Java 17 and later, replace String.format loops with java.util.HexFormat.of().formatHex(bytes) for significantly better performance and memory efficiency without sacrificing readability.
## 2026-07-09 - Redundant Optimization PR Rejected
**Learning:** PRs containing optimizations that have already been merged to the main branch via another PR will cause conflicts and be closed as obsolete/duplicates.
**Action:** Always ensure you pull the absolute latest changes from the target branch (e.g., main) before identifying and implementing performance optimizations to avoid duplicating work.

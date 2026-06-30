## 2024-06-30 - Hex Format Optimization
**Learning:** Using String.format() in a loop to convert byte arrays to hex strings allocates many temporary objects (String, Object[] for varargs) and performs poorly. The application's DefaultDocumentConversionService previously used this pattern for hashing uploads, impacting memory and CPU heavily.
**Action:** Always prefer java.util.HexFormat.of().formatHex(bytes) introduced in Java 17 for byte-to-hex conversion to improve performance, minimize object allocation, and make the code cleaner.

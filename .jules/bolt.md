## 2026-07-09 - 16진수 문자열 변환 성능 최적화
**Learning:** 루프 내에서 `String.format("%02x", b)`를 사용하는 것은 지속적인 객체 할당과 파싱으로 인해 Java에서 매우 비효율적입니다. Java 17 이상에서는 이 사용 사례에 최적화된 `java.util.HexFormat`을 제공합니다.
**Action:** 바이트 배열을 16진수 문자열로 변환할 때 성능을 향상시키고 메모리 할당을 줄이기 위해 `java.util.HexFormat.of().formatHex(bytes)`를 사용해야 합니다.

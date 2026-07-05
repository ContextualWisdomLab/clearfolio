## 2026-07-05 - ⚡ Bolt: HexFormat Optimization

**Learning:** `java.util.HexFormat.of()`를 매번 호출하여 사용하거나, `String.format("%02x", byte)` 루프를 통해 바이트 배열을 16진수 문자열로 변환하는 것은 성능에 영향을 미치는 중간 객체 할당(intermediate allocations)을 발생시킵니다.
**Action:** 바이트 배열에서 16진수 문자열로의 변환 시 `private static final java.util.HexFormat HEX_FORMAT = java.util.HexFormat.of();`와 같이 정적 상수를 선언하고, `HEX_FORMAT.formatHex(bytes)`를 사용하여 성능을 최적화하고 메모리 사용량을 줄입니다.

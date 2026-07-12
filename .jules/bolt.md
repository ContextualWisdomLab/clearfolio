## 2026-07-09 - 16진수 문자열 변환 성능 최적화
**Learning:** 루프 내에서 `String.format("%02x", b)`를 사용하는 것은 지속적인 객체 할당과 파싱으로 인해 Java에서 매우 비효율적입니다. Java 17 이상에서는 이 사용 사례에 최적화된 `java.util.HexFormat`을 제공합니다.
**Action:** 바이트 배열을 16진수 문자열로 변환할 때 성능을 향상시키고 메모리 할당을 줄이기 위해 `java.util.HexFormat.of().formatHex(bytes)`를 사용해야 합니다.

## 2026-07-12 - ByteArrayInputStream 생성 시 방어적 복사 제거
**Learning:** `ByteArrayInputStream`은 제공된 바이트 배열을 내부적으로 복사하지 않고 단순히 감싸기(wrap)만 합니다. 파일 업로드와 같은 큰 데이터를 메모리에서 다룰 때 `getInputStream()` 호출 시마다 전체 바이트 배열을 `Arrays.copyOf()`로 방어적 복사(defensive copy)하는 것은 심각한 메모리 할당 오버헤드와 가비지 컬렉션 부하를 일으킵니다.
**Action:** 내부 바이트 배열을 불변(immutable)으로 취급할 수 있는 경우, `ByteArrayInputStream`을 생성할 때 원본 바이트 배열을 방어적 복사 없이 직접 전달하여 대용량 메모리 할당을 방지해야 합니다.

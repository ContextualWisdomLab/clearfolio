## 2026-07-09 - Reuse HexFormat instance to reduce allocations and loops
**Learning:** Using `String.format("%02x", b)` in a loop or calling `HexFormat.of()` repeatedly for hex conversions creates unnecessary intermediate object allocations and reduces performance.
**Action:** Always use a single, reusable `private static final java.util.HexFormat HEX_FORMAT = java.util.HexFormat.of();` instance and its `formatHex` method for byte-to-hex string conversions.
## 2026-07-09 - 16진수 문자열 변환 성능 최적화
**Learning:** 루프 내에서 `String.format("%02x", b)`를 사용하는 것은 지속적인 객체 할당과 파싱으로 인해 Java에서 매우 비효율적입니다. Java 17 이상에서는 이 사용 사례에 최적화된 `java.util.HexFormat`을 제공합니다.
**Action:** 바이트 배열을 16진수 문자열로 변환할 때 성능을 향상시키고 메모리 할당을 줄이기 위해 `java.util.HexFormat.of().formatHex(bytes)`를 사용해야 합니다.
## 2026-07-10 - 16진수 문자열 변환 성능 최적화 (2)
**Learning:** 테스트 환경 설정, 예컨대 `pom.xml`의 `-Werror`를 지우는 식의 환경 저하는 프로덕션 레벨 품질 관리에 심각한 결함을 초래할 수 있으므로, 어떤 최적화나 개선 시에도 기존의 품질 표준을 훼손해서는 안 됩니다. 또한 사용자의 100% 테스트 커버리지 요구 사항이 있을 시 반드시 이를 만족하기 위해 노력해야 합니다.
**Action:** 코드 개선 작업에서 기존 빌드 제약사항(`-Werror` 등)을 우회하지 않고, 이를 준수하면서 최적화를 수행하도록 하며 요구된 기준(100% 테스트 커버리지 등)을 빠짐없이 이행합니다.
## 2026-07-11 - 문자열 조작 시 불필요한 객체 생성 방지 성능 최적화
**Learning:** 문자열을 순회하며 검증 또는 변환을 수행할 때 `StringBuilder`를 무조건 생성하는 것은 원본 문자열이 이미 깨끗하거나 조건을 만족하는 '해피 패스(fast path)'에서도 불필요한 객체 할당 오버헤드를 발생시킵니다.
**Action:** 문자열을 먼저 순회하여 변환 또는 할당이 정말로 필요한지 확인한 후(예: 허용되지 않는 문자가 처음으로 등장한 인덱스 확보), 필요한 경우에만 객체 할당(예: `StringBuilder`)을 수행하여 메모리 사용량과 성능을 최적화해야 합니다.
## 2026-07-12 - 16진수 문자열 변환 성능 최적화 (3)
**Learning:** 루프 내에서 `String.format(\"%02x\", b)`를 사용하는 것은 성능에 매우 큰 악영향을 미칩니다. 이전에 발견되었으나 아직 수정되지 않은 `FileSystemArtifactStore.java` 내의 변환 로직이 남아 있었습니다.
**Action:** 앞으로도 코드베이스 내에 남아 있는 `String.format` 기반의 비효율적 16진수 변환을 모두 찾아 `java.util.HexFormat.of().formatHex(bytes)`로 교체해야 합니다.
## 2026-07-12 - ByteArrayInputStream 생성 시 방어적 복사 제거
**Learning:** ByteArrayInputStream은 전달받은 바이트 배열을 내부적으로 읽기 전용으로만 사용하며 변경하지 않으므로, 불변이거나 읽기 전용으로 사용될 배열을 전달할 때 불필요하게 Arrays.copyOf()로 방어적 복사를 할 필요가 없습니다.
**Action:** ByteArrayInputStream 생성자에 불변/읽기 전용 배열을 전달할 때 방어적 복사 없이 원본 바이트 배열을 직접 전달하여 메모리 할당 및 가비지 컬렉션 오버헤드를 줄입니다.
## 2026-07-13 - 단일 패스 문자열 치환 최적화 (O(N) 단일 스캔 및 지연 할당)
**Learning:** `String.replace()`를 여러 번 체이닝하여 호출하면, 문자열 치환이 발생하지 않는 경우에도 내부적으로 불필요한 스캔이 중복 발생하고, 치환 시마다 새로운 문자열 객체와 char 배열이 할당되어 메모리 낭비와 성능 저하(GC 압박)가 발생한다.
**Action:** 여러 문자를 한 번에 치환해야 하는 경우, O(N) 단일 스캔을 통해 `charAt()`으로 문자를 확인하고, 치환이 실제로 필요한 경우에만 `StringBuilder`를 지연 할당(Lazy allocation)하여 성능을 최적화하고 불필요한 메모리 할당을 방지한다.
## 2026-07-14 - 뷰어 쉘 HTML 렌더링 성능 최적화
**Learning:** 크기가 큰 텍스트 블록에 대해 여러 번의 `.replace()` 메서드 체이닝을 사용할 경우, 호출할 때마다 새로운 문자열 객체가 생성되어 불필요한 메모리 할당이 발생합니다.
**Action:** 긴 문자열 템플릿에 동적 변수를 삽입해야 할 때는 정적인 템플릿 상수(static final)들로 분리한 뒤 문자열 연결(concatenation)을 사용하면, 컴파일 시 `StringConcatFactory`를 통해 최적화되어 요청당 객체 할당량을 크게 줄일 수 있습니다.

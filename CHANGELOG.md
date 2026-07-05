# 변경 로그

## [Unreleased]

### 성능 개선 (Performance)
- `ArtifactLinkService` 및 `DefaultDocumentConversionService` 내부에서 바이트 배열을 16진수 문자열로 변환할 때 불필요하게 객체 할당(가비지 컬렉션 부하)을 야기하는 `String.format("%02x", b)` 루프를 Java 17 표준의 `java.util.HexFormat.formatHex(raw)`로 최적화했습니다.
- `DefaultDocumentValidationService`를 포함한 여러 서비스에서 `HexFormat.of()`를 `static final` 상수로 추출해 매번 인스턴스를 생성하지 않고 재사용하도록 변경했습니다.

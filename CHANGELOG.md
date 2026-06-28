# 변경 사항

## [Unreleased]
### 성능 개선 (Performance)
- `DefaultDocumentConversionService` 내부의 `String.format` 루프를 사용하던 비효율적인 Hex 문자열 변환 로직을 `java.util.HexFormat`을 사용하도록 변경하여 성능 크게 최적화

# CHANGELOG

## [Unreleased]

### 추가
- ConversionJob 삭제 기능 추가 (`DELETE /api/v1/convert/jobs/{jobId}`). 작업과 관련 아티팩트(PDF)를 함께 삭제합니다.

### 변경
- `DefaultDocumentConversionService` 내부의 `contentHash` 생성 로직을 `String.format` 루프 방식에서 `java.util.HexFormat`을 사용하도록 변경하여 메모리 할당 및 성능을 최적화했습니다.
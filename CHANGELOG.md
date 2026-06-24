# Changelog

## [0.1.0] - 2026-06-25

### 추가된 기능 (Added)
- **PDF 다운로드 API 추가 (`GET /api/v1/convert/jobs/{jobId}/download`)**
  - 변환 성공한 작업에 대한 PDF 바이너리 다운로드 엔드포인트를 구현했습니다.
  - 파일 다운로드 시 원본 파일명 기반의 `.pdf` 확장자 처리와 파일 무결성을 위한 체크섬(`X-Checksum-Sha256`) 헤더를 응답에 포함하도록 지원합니다.

- **관리자용 전체 작업 조회 API 추가 (`GET /api/v1/admin/convert/jobs`)**
  - 시스템 내 전체 변환 작업 내역을 조회할 수 있는 Admin 엔드포인트를 구현했습니다.
  - `deadLettered` 필터 조건을 쿼리 파라미터로 제공하여 실패한 작업들만 조회할 수 있습니다.
  - 관련 `AdminJobListResponse` DTO 모델과 이를 처리하는 Repository 및 Service 계층의 `findAll`/`getAllJobs` 메서드를 추가했습니다.

### 테스트 커버리지 (Tests)
- 신규 구현된 Repository, Service, Controller 계층에 대한 유닛 테스트(Unit Tests)를 작성하여 JaCoCo 기준 라인 및 브랜치 커버리지 100%를 달성했습니다.

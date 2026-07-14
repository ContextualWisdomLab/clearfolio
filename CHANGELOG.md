## [Unreleased]
### Added
- **UI UX 개선**: 'Details' 버튼 클릭 시, 작업 상세 정보 로드 중에 사용자가 명시적인 로딩 상태를 확인할 수 있도록 'Loading...' 텍스트와 비활성화 상태를 표시하도록 추가했습니다.

# Changelog

## [Unreleased]

### 추가된 기능 (Added)
- **PII 로깅 취약점 패치**: `DefaultDocumentValidationService`에서 평문으로 노출되던 승인자 식별자(`approverId`)를 로깅 전 해싱하도록 수정했습니다.

- **관리자용 단건 작업 삭제 및 재시도 API 추가**
  - 특정 변환 작업을 삭제할 수 있는 `DELETE /api/v1/admin/convert/jobs/{jobId}` 엔드포인트를 추가했습니다.
  - 실패(dead-lettered) 상태인 작업을 관리자가 재시도 큐에 등록할 수 있는 `POST /api/v1/admin/convert/jobs/{jobId}/retry` 엔드포인트를 추가했습니다.

- **비동기 버튼 로딩 피드백 및 상태 복원 개선**
  - KPI 스냅샷 증거를 다시 불러오는 `refreshKpiEvidence` 동작 중에 "Refresh evidence" 버튼을 비활성화하고 "Refreshing..." 이라는 피드백을 제공하여 사용자의 중복 클릭을 방지했습니다.
  - 버튼 상태 변경 시 내부 DOM 구조를 보존하기 위해 `Array.from(button.childNodes)`로 원래 노드를 저장하고, 성공 및 실패 후 `finally` 블록에서 `replaceChildren(...)`으로 안전하게 복원하도록 구현했습니다.


## [0.1.0] - 2026-06-25


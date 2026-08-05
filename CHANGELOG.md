# Changelog

## [Unreleased]

### Added

- **UI UX 개선**: 'Details' 버튼 클릭 시, 작업 상세 정보 로드 중에 사용자가 명시적인 로딩 상태를 확인할 수 있도록 'Loading...' 텍스트와 비활성화 상태를 표시하도록 추가했습니다.
- **관리자용 단건 작업 삭제 및 재시도 API 추가**
  - 특정 변환 작업을 삭제할 수 있는 `DELETE /api/v1/admin/convert/jobs/{jobId}` 엔드포인트를 추가했습니다.
  - 실패(dead-lettered) 상태인 작업을 관리자가 재시도 큐에 등록할 수 있는 `POST /api/v1/admin/convert/jobs/{jobId}/retry` 엔드포인트를 추가했습니다.
- **비동기 버튼 로딩 피드백 및 상태 복원 개선**
  - KPI 스냅샷 증거를 다시 불러오는 `refreshKpiEvidence` 동작 중에 "Refresh evidence" 버튼을 비활성화하고 "Refreshing..."이라는 피드백을 제공하여 사용자의 중복 클릭을 방지했습니다.
  - 버튼 상태 변경 시 내부 DOM 구조를 보존하기 위해 `Array.from(button.childNodes)`로 원래 노드를 저장하고, 성공 및 실패 후 `finally` 블록에서 `replaceChildren(...)`으로 안전하게 복원하도록 구현했습니다.

### Changed

- PDF.js WebJar를 `6.1.200`으로 올리고, Clearfolio가 동일 버전의 `pdf.mjs`와 `pdf.worker.mjs`를 직접 사용해 서명된 same-origin artifact의 첫 페이지를 렌더링하도록 통합했습니다. 패키징·셸 경로·서명된 `artifactToken` 흐름을 회귀 테스트로 고정했습니다.
- CI가 pull request의 정확한 head SHA를 명시적으로 체크아웃하고 검증하며, 합성 merge revision은 별도 호환성 작업에서 검증하도록 분리했습니다.
- Maven `verify` 단계에서 JaCoCo production line 및 branch missed count가 각각 0인지 강제하고, 실패 시 누락 위치 진단을 출력하도록 했습니다.
- Jazzer fuzzing도 pull request의 정확한 head SHA를 명시적으로 체크아웃하고 검증하도록 강화했습니다.

### Security

- 정책 재정의 승인자의 원문 식별자를 감사 로그에서 제거하고, 전용 회전형 키와 도메인 분리를 사용하는 HMAC 기반 `approverFingerprint`로 대체했습니다. 전용 키가 없으면 원문이나 비키 해시로 폴백하지 않고 비상관 `unavailable` 표식을 기록합니다.
- 감사 가명화 키의 소유권, 회전, 보존, 사고 대응 및 GDPR상 가명정보의 개인정보 지위를 문서화하고, 원문 승인자 식별자와 승인 토큰이 로그에 남지 않는 회귀 테스트를 추가했습니다.
- 경로·쿼리 파라미터 타입 변환 실패 응답에서 사용자가 제출한 거부 값을 고정된 `[redacted]` 표식으로 대체해 오류 응답을 통한 개인정보·비밀값 반사를 차단했습니다. 값이 실제로 없었던 경우에만 `null` 진단을 유지합니다.
- 관리자 API에 서명된 tenant claim 검증, `admin:read`/`admin:write` 최소 권한, tenant 소유권 검사를 적용했습니다. 누락 및 cross-tenant 객체는 동일한 not-found 응답으로 은폐합니다.
- 관리자 목록 조회는 repository 경계에서 tenant predicate를 적용한 뒤 선택적 dead-letter 필터를 처리하도록 변경했습니다.
- 관리자 delete/retry가 검증된 `TenantContext`를 tenant-aware service mutation boundary에 전달하도록 변경해 controller 우회 호출에서도 소유권 검사가 적용되도록 했습니다.
- tenant-aware service 기본 구현은 global lookup 또는 legacy mutation을 호출하지 않고 `false`/`NOT_FOUND`로 실패 종료합니다. 모듈형 대체 adapter는 원자적 tenant-scoped mutation을 명시적으로 구현해야 관리자 작업을 성공시킬 수 있습니다.
- 관리자 delete/retry는 `deleteByTenantAndId`와 `retryDeadLetteredForTenant` 원자적 tenant-scoped persistence contract를 사용합니다. artifact cleanup은 소유권이 확인된 repository 삭제가 성공한 뒤에만 실행되며, worker enqueue는 소유권 확인과 dead-letter 상태 전이가 원자적으로 성공한 뒤에만 실행됩니다.
- tenant-scoped lookup/delete/retry는 누락된 job UUID를 예외로 노출하지 않고 empty/`false`/`NOT_FOUND`로 실패 종료하며 기존 저장 상태를 변경하지 않습니다.
- tenant-and-content-hash 보조 인덱스가 현재 UUID 레코드의 tenant/hash와 일치하는지 재검증하고, 동일 UUID 교체 시 이전 인덱스를 제거하며, find-or-store UUID 충돌을 fail-closed 처리해 stale 인덱스나 stale 관찰이 다른 tenant 작업으로 해석되는 경로를 차단했습니다.
- tenant-scoped 삭제와 동일 UUID 교체가 동시에 발생해도 primary job map과 tenant-content secondary index가 하나의 임계 구역에서 함께 갱신되도록 직렬화해, 교체 작업의 유효 인덱스를 지연된 삭제 정리가 제거하는 경쟁 조건을 차단했습니다.
- 관리자 허용·거부·미존재·재시도 불가·실패 결정을 actor/tenant별 도메인 분리 HMAC 지문으로 기록하고, raw subject·tenant·claim signature·문서 메타데이터가 감사 로그와 retry provenance에 남지 않도록 했습니다.
- buyer-demo profile의 tenant-claims HMAC secret 환경변수 직접 바인딩을 제거하고 공통 Spring config-tree secret mount에서 읽도록 변경했습니다.

### Fixed

- 뷰어 UI의 재시도 버튼 로딩 상태가 내부 DOM을 손상시키지 않고 안전하게 복원되도록 수정했습니다.

## [0.1.0] - 2026-06-25

### 추가된 기능 (Added)

- **비동기 버튼 로딩 상태 UX 개선 (Async Button Loading States)**
  - 문서 제출(`submitDocument`), 데모 데이터 로드(`loadDemoData`), 실패 작업 재시도(`retryActiveJob`) 등 비동기 요청을 수행하는 버튼들에 대해 처리 중 명시적인 로딩 상태(Loading, Submitting, Retrying 등)를 추가했습니다.
  - 사용자의 중복 클릭을 방지하기 위해 작업 중에는 버튼이 비활성화되도록 수정했습니다.
  - 임시 로딩 상태 적용 후, 원래 버튼 내부에 존재할 수 있는 중첩 DOM 노드(아이콘 등)가 보존될 수 있도록 `childNodes`를 임시 저장하고 `replaceChildren(...)`으로 복원하는 방식으로 구현했습니다.

- **PDF 다운로드 API 추가 (`GET /api/v1/convert/jobs/{jobId}/download`)**
  - 변환 성공한 작업에 대한 PDF 바이너리 다운로드 엔드포인트를 구현했습니다.
  - 파일 다운로드 시 원본 파일명 기반의 `.pdf` 확장자 처리와 파일 무결성을 위한 체크섬(`X-Checksum-Sha256`) 헤더를 응답에 포함하도록 지원합니다.

- **관리자용 전체 작업 조회 API 추가 (`GET /api/v1/admin/convert/jobs`)**
  - 시스템 내 전체 변환 작업 내역을 조회할 수 있는 Admin 엔드포인트를 구현했습니다.
  - `deadLettered` 필터 조건을 쿼리 파라미터로 제공하여 실패한 작업들만 조회할 수 있습니다.
  - 관련 `AdminJobListResponse` DTO 모델과 이를 처리하는 Repository 및 Service 계층의 `findAll`/`getAllJobs` 메서드를 추가했습니다.

### 테스트 커버리지 (Tests)

- 신규 구현된 Repository, Service, Controller 계층에 대한 유닛 테스트(Unit Tests)를 작성하여 JaCoCo 기준 라인 및 브랜치 커버리지 100%를 달성했습니다.

### 보안 (Security)

- **의존성 취약점 일괄 정리 (trivy-fs / osv-scan 대응)**: Spring Boot 부모 POM을 `3.5.0`에서 `3.5.16`으로 올려 Spring Framework, Netty, Reactor Netty, logback 관련 다수의 HIGH/MEDIUM 권고를 해소했습니다.
- Jackson 계열을 `jackson-bom` import로 `2.22.1`에 고정하여 jackson-databind case-insensitive deserialization bypass 권고(GHSA-5jmj-h7xm-6q6v / CVE-2026-54515)를 제거했습니다.
- Apache Tika 표준 파서를 통해 유입되던 전이 의존성을 `dependencyManagement`로 고정했습니다: junrar `7.6.0`(경로 순회 RCE/파일 쓰기), commons-io `2.20.0`(XmlStreamReader DoS), commons-lang3 `3.18.0`, BouncyCastle `bcprov-jdk18on 1.84` 및 `bcpkix-jdk18on 1.84`(CRITICAL/Medium). 전체 347개 테스트 통과를 확인했습니다.
- `jackson-databind`도 `2.22.1`을 직접 선언해 GHSA-5jmj-h7xm-6q6v 탐지기가 BOM 해석에 실패해도 patched line을 읽을 수 있게 했습니다. OSV Scanner v2.3.8이 advisory 본문상 patched인 `2.22.1`을 계속 매칭하므로 루트 `osv-scanner.toml`에는 이 GHSA만 2026-08-15까지 좁게 예외 처리했습니다.
- 과거 QA 증거 SBOM의 Jackson purl/ref도 `2.21.5`로 갱신해 Scorecard/OSV가 저장소 내 stale SBOM을 취약 의존성으로 재탐지하지 않게 했습니다.
- 루트 `LICENSE`와 Maven license metadata를 추가해 Scorecard License alert가 표준 Apache-2.0 파일을 확인할 수 있게 했습니다.
- logback-core 신규 권고(GHSA-jhq6-gfmj-v8fx) 대응을 위해 Logback 관리 버전을 `1.5.35`로 고정했습니다.
- 저장소 보안 정책, Maven/GitHub Actions Dependabot 설정, 기본 CodeQL/중앙 SAST 운영 지침, 다운로드 파일명 정규화 Jazzer fuzz target을 추가해 Scorecard 보안 거버넌스 신호를 보강했습니다.

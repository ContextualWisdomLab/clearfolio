## 2026-06-26 - [정책 우회 서명을 위한 암호화 검증 추가]
**Vulnerability:** 정책 우회 기능이 임의의 (잠재적으로 하드코딩되거나 유출될 수 있는) 문자열 토큰(`approvalToken`)에 의존하고 있어, 엄격한 암호화 서명 검증을 요구하는 시스템 보안 표준을 위반했습니다.
**Learning:** 차단된 확장자 업로드와 같은 중요한 정책의 우회는 반드시 인증되어야 하며 무결성이 검증되어야 합니다. 이전에는 단순히 토큰과 승인자 ID의 존재 여부만 확인했을 뿐, 수학적으로 연결하지 않았습니다.
**Prevention:** HMAC-SHA256 서명 검증을 구현했습니다. 이제 `approvalToken`은 `approverId + ":" + extension`의 HMAC 서명값이어야 하며, `conversion.policy-override-secret`을 통해 구성된 시크릿으로 서명됩니다. 서명 비교 시 타이밍 공격을 방지하기 위해 `MessageDigest.isEqual`을 사용했습니다.

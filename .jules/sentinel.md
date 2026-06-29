## 2026-06-26 - [정책 우회 서명을 위한 암호화 검증 추가]
**Vulnerability:** 정책 우회 기능이 임의의 문자열 토큰(`approvalToken`) 또는 공유 시크릿 기반 HMAC 서명 검증에 의존하고 있어 보안 검사 도구(Strix)에 의해 심각도 높은 취약점으로 식별되었습니다. 공유 시크릿이 유출될 경우 검증 로직이 무력화될 수 있습니다.
**Learning:** 차단된 확장자 업로드와 같은 중요한 정책 우회는 공유 시크릿에 의존하지 않는 안전한 공개키/비공개키 암호화 기반 서명 검증이 필요합니다.
**Prevention:** RSA-SHA256(`SHA256withRSA`) 기반의 서명 검증 로직을 구현했습니다. 이제 `approvalToken`은 `approverId + ":" + extension`에 대한 RSA 서명값이어야 하며, `conversion.policy-override-public-key`에 설정된 공개키로만 검증됩니다.

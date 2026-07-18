import re

with open('CHANGELOG.md', 'r') as f:
    content = f.read()

new_content = re.sub(
    r'(### 추가된 기능 \(Added\))',
    r'\1\n- **관리자 API 권한 검증 로직 추가**: `AdminController`의 엔드포인트에 `TenantAccessService`를 통한 `ADMIN_READ`, `ADMIN_WRITE` 권한 검증을 추가하여 보안을 강화했습니다.',
    content,
    count=1
)

with open('CHANGELOG.md', 'w') as f:
    f.write(new_content)

import re

with open('CHANGELOG.md', 'r', encoding='utf-8') as f:
    content = f.read()

fixed_entry = "- 뷰어 UI의 재시도 버튼 로딩 상태가 내부 DOM을 손상시키지 않고 안전하게 복원되도록 수정했습니다.\n"

if fixed_entry not in content:
    if "### Fixed\n\n" in content:
        content = content.replace("### Fixed\n\n", f"### Fixed\n\n{fixed_entry}")
    else:
        content = content.replace("## [Unreleased]\n\n", f"## [Unreleased]\n\n### Fixed\n\n{fixed_entry}\n")

    with open('CHANGELOG.md', 'w', encoding='utf-8') as f:
        f.write(content)

import json

comments = [
  {
    "comment_id": "5851080572",
    "body": "Exact-head admission audit: `7fa2fc9549bd232fdc685286eb74dd4ba9c30be9` (base `main@06633a25109c62e24a7015ae04fb9f6e0a246f7e`, 1 ahead / 0 behind).\n\n현재 blocker:\n- terminal workflow: CI=failure\n- unresolved review threads: 2\n\n유효 commit·diff·review evidence를 보존한 채 Draft/Proposed로 교정합니다. Base 이동이나 queue 대기만으로 Close하지 않으며 Force Push·synthetic status/approval·manual rerun·bypass를 사용하지 않습니다. Blocker 수리 후 새 exact head에서 Checks와 review admission을 다시 받아야 합니다."
  }
]
print(json.dumps(comments, indent=2))

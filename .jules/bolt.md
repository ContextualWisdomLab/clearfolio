## 2026-09-09 - String.split 성능 최적화
**Learning:** `String.split()`을 사용하면 불필요한 배열 및 정규식 평가로 인해 성능 저하가 발생할 수 있습니다. 특히 대용량 데이터를 처리하는 메서드에서 GC(Garbage Collector)의 압력을 높입니다.
**Action:** `String.split()` 대신 수동으로 `indexOf()` 및 `substring()`을 사용하는 방식으로 파싱 로직을 최적화하여 메모리 및 성능 문제를 개선합니다.
## 2026-09-09 - String.split 성능 최적화
**Learning:** `String.split()`을 사용하면 불필요한 배열 및 정규식 평가로 인해 성능 저하가 발생할 수 있습니다. 특히 대용량 데이터를 처리하는 메서드에서 GC(Garbage Collector)의 압력을 높입니다.
**Action:** `String.split()` 대신 수동으로 `indexOf()` 및 `substring()`을 사용하는 방식으로 파싱 로직을 최적화하여 메모리 및 성능 문제를 개선합니다.

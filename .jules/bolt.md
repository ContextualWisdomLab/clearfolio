## 2026-06-23 - Java 17+ HexFormat 최적화 활용
**Learning:** 해시 값을 Hex 문자열로 변환할 때, 반복문 내에서 `String.format("%02x", b)`를 사용하는 것은 정규표현식 파싱 및 객체 생성 오버헤드로 인해 성능 저하의 주된 원인이 됩니다. Java 17 이상부터는 `java.util.HexFormat`을 사용하여 이 과정을 매우 빠르게 처리할 수 있습니다.
**Action:** 바이트 배열을 Hex 문자열로 변환할 때는 항상 `HexFormat.of().formatHex(bytes)`를 사용하여 성능과 가비지 컬렉션 부하를 최적화합니다.

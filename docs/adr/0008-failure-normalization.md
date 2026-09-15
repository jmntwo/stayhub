# ADR 0008. 실패 판정 통일

- 상태: 확정
- 날짜: 2026-09-14

## 배경

공급사마다 실패를 다르게 알림. A는 HTTP 상태 코드, B는 HTTP 200 + 본문 resultCode. 타임아웃과 연결 실패는 HTTP 응답 자체가 없음. 검색 서비스가 이 차이를 모르고 "어느 공급사가 어떤 이유로 실패했는가"만 다룰 수 있어야 함

## 검토했다 폐기한 대안

- **예외 클래스 계층** (SupplierException 아래 TimeoutException, UnavailableException, RejectedException): 응답의 reason 값으로 바꿀 때 클래스 → 문자열 변환이 필요함, 사유가 늘면 클래스도 늘어남
- **어댑터가 실패를 예외 대신 결과 객체로 반환**: 성공 경로와 실패 경로가 한 타입에 섞여 Mono 조합에서 분기가 늘어남

## 결정

- 어댑터가 던지는 예외는 SupplierException 하나. supplier + reason(FailureReason)
- FailureReason은 검색 응답 suppliers[].reason과 같은 enum으로 두어 예외의 사유가 변환 없이 응답의 사유가 됨

| 상황          | A | B | reason | retryable |
|-------------|---|---|---|---|
| 응답, 연결 타임아웃 | ReadTimeout, ConnectTimeout | 동일 | TIMEOUT | O |
| 공급사 장애      | HTTP 5xx, 연결 거부 | HTTP 5xx, resultCode E5xx, 연결 거부 | UNAVAILABLE | O |
| 요청 거절       | HTTP 4xx | HTTP 4xx, resultCode E4xx | REJECTED | X |
| 응답 파싱 실패    | 깨진 JSON | 깨진 JSON, 성공 코드인데 data null | UNAVAILABLE | O |
| 매핑 없음       | 어댑터 아님. 검색 서비스가 판정 | 동일 | NO_MAPPING | X |

- B는 HTTP 200이라도 resultCode가 "0000"이 아니면 실패 (본문 코드 판정이 먼저)
- 429(호출 한도 초과)는 REJECTED. 재시도 정책을 구현하면 별도 사유로 뗄지 재검토
- 타임아웃 판정은 원인 사슬을 따라감. reactor-netty의 타임아웃 예외가 Spring 예외에 감싸여 오기 때문

## 결과

- 검색 서비스는 SupplierException의 supplier와 reason만 봄
- retryable로 재시도 대상이 예외 자체에 표시됨. 재시도 정책 구현 시 이 값만 참조 예정 
- 비용: HTTP 계층 판정 코드가 A, B 어댑터에 각각 존재. 공급사 2개 규모에서는 파일 하나만 읽어도 규칙이 보이는 중복 유지를 택함 (공급사가 늘면 공통 헬퍼로 추출 검토)

## AI 제안과 내 판단

- AI가 예외 계층과 "예외 하나 + enum" 둘을 제시하고 후자를 추천. 응답 어휘와 같은 enum을 쓰면 변환 코드가 없다는 점에서 수용

# ADR 0002. Mock Supplier 구성 방식

- 상태: 확정
- 날짜: 2026-09-12

## 배경

스펙대로 Supplier A·B를 흉내내는 Mock을 직접 만들어야 하고, Mock은 다음 상황을 재현해야 함

| 상황 | A | B |
|---|---|---|
| 정상 | 200 + 정상 본문 | 200 + `resultCode: 0000` |
| 공급사 장애 | HTTP 503 | 200 + `resultCode: E503` |
| 무응답 | 연결은 되나 응답 없음 | 동일 |
| 응답 지연 (선택) | N초 뒤 정상 응답 | 동일 |


## 검토했다 폐기한 대안

- **WireMock 하나로 런타임과 테스트 모두 처리**: 고장 모드 전환이 자체 엔드포인트보다 번거로움
- **자체 Mock 모듈 하나로 런타임과 테스트 모두 처리**: 무응답 재현이 600초 대기가 되고, 테스트가 mock-supplier 실행 여부에 종속됨
- **본 애플리케이션 안의 테스트용 컨트롤러**: 같은 포트 금지에 걸림

## 결정

런타임 Mock과 테스트 Mock을 용도에 따라 다른 도구로 **나눈다.**

- 런타임: mock-supplier 모듈 (9090) 
  - 고정 응답. 날짜만 요청의 checkIn~checkOut에 맞춰 생성
  - 고장 모드는 (공급사, API)별로 독립. POST /control/{a|b}/{catalog|availability}/mode로 정상, 장애, 무응답, 지연 전환
  - catalog는 숙소 목록 API, availability는 재고·요금 API
- 테스트: WireMock
  - 지연·무응답을 ms 단위로 지정

## 결과

- 실행자는 mock-supplier를 띄운 뒤 curl로 A 무응답, B 장애를 만들 수 있음
- 목록 API만 고장내면 매핑 실패(NO_MAPPING), 재고·요금 API만 고장내면 검색 장애(UNAVAILABLE, TIMEOUT)를 순서 조작 없이 구분해 재현
- 자동 테스트는 외부 프로세스 없이 ./gradlew test로 가능
- 비용: 응답 데이터가 두 모듈에 각각 존재하게 됨

## AI 제안과 내 판단

- WireMock, 별도 모듈, 테스트용 컨트롤러 세 방식의 차이를 AI에게 물었고, "런타임은 별도 모듈, 테스트는 WireMock" 제안을 수용함. 실행자의 편의와 Mock 포트 분리 제약을 고려해 타당하다고 판단
- AI는 고장 모드를 공급사 단위 스위치 하나로 두고 목록 API와 재고·요금 API가 같은 모드를 따르게 하자고 제안. 그러나 나는 매핑 실패와 검색 장애를 구분해 재현하기 위해 앱 기동 순서를 조작해야 하는 점이 번거롭다고 판단해 API별 스위치로 나눔
- AI는 API 구분 이름으로 hotels, availability를 제안. A의 경로를 그대로 쓴 이름이라 B와 맞지 않아 역할 이름 list, availability로 변경. 이후 어댑터 코드의 fetchCatalog, CatalogEntry와 이름을 맞추기 위해 catalog로 재변경
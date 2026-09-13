# API 명세

Swagger UI: `http://localhost:8080/swagger-ui.html`

이 문서는 응답 구조와 부분 실패 예시를 설명, 필드 상세는 Swagger가 기준

## 통합 검색

```
GET /api/v1/stays/search?checkIn=2026-10-01&checkOut=2026-10-03&adults=2&children=0
```

### 요청

| 파라미터 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| checkIn | date (YYYY-MM-DD) | O | |
| checkOut | date (YYYY-MM-DD) | O | checkIn보다 뒤. 체크아웃일은 숙박일에 포함되지 않음 |
| adults | int | O | 1 이상 |
| children | int | O | 0 이상 |

### 응답 구조

```
{
  "checkIn", 
  "checkOut", 
  "nights",
  "adults", 
  "children",
  "suppliers": [ { "supplier", "status", "reason" } ],
  "items":     [ StayOffer ]
}
```

- `suppliers`: 공급사별 조회 상태. 항상 모든 공급사가 한 요소씩 포함됨
- `items`: 표준 상품 목록

| suppliers[].status | 뜻 |
|---|---|
| OK | 모든 청크 성공 |
| PARTIAL | 일부 청크 실패. 결과 일부 누락 |
| FAILED | 전부 실패 또는 매핑 없음. 이 공급사 결과 없음 |

| suppliers[].reason | 뜻 |
|---|---|
| TIMEOUT | 연결 또는 응답 타임아웃 |
| UNAVAILABLE | 공급사 장애. 5xx, E5xx, 연결 실패 |
| REJECTED | 공급사가 요청을 거절. 4xx, E4xx |
| NO_MAPPING | 매핑이 아직 없음 |

### 예시

아래 예시 JSON은 AI가 초안을 만들고, 필드와 값이 domain-model.md, ADR 0005, ADR 0006과 일치하는지 검토한 뒤 적용함. 숙소 및 요금 데이터는 Mock 데이터와 같음

### 예시 1. 정상

```json
{
  "checkIn": "2026-10-01",
  "checkOut": "2026-10-03",
  "nights": 2,
  "adults": 2,
  "children": 0,
  "suppliers": [
    { "supplier": "A", "status": "OK" },
    { "supplier": "B", "status": "OK" }
  ],
  "items": [
    {
      "propertyId": 1,
      "propertyName": "Harbor View Hotel",
      "roomTypeId": 1,
      "roomTypeName": "Ocean Twin",
      "supplier": "A",
      "maxOccupancy": 2,
      "availableRooms": 2,
      "price": {
        "total": 286000,
        "currency": "KRW",
        "nightly": [
          { "date": "2026-10-01", "rate": 130000, "tax": 13000 },
          { "date": "2026-10-02", "rate": 130000, "tax": 13000 }
        ]
      },
      "breakfastIncluded": false
    },
    {
      "propertyId": 3,
      "propertyName": "Harbor View Hotel",
      "roomTypeId": 5,
      "roomTypeName": "Ocean Twin Room",
      "supplier": "B",
      "maxOccupancy": 2,
      "availableRooms": 1,
      "price": {
        "total": 310000,
        "currency": "KRW",
        "nightly": null
      },
      "breakfastIncluded": true
    },
    {
      "propertyId": 2,
      "propertyName": "Pine Hill Stay",
      "roomTypeId": 3,
      "roomTypeName": "Standard Double",
      "supplier": "A",
      "maxOccupancy": 2,
      "availableRooms": 0,
      "price": {
        "total": 190000,
        "currency": "KRW",
        "nightly": [
          { "date": "2026-10-01", "rate": 90000, "tax": 9000 },
          { "date": "2026-10-02", "rate": 82000, "tax": 9000 }
        ]
      },
      "breakfastIncluded": false
    }
  ]
}
```

- 같은 숙소가 A와 B에서 각각 옴. 병합하지 않고 출처와 조식 여부로 구분
- B는 날짜별 요금이 없어 `nightly: null`
- Pine Hill Stay는 둘째 날 재고가 0이라 `availableRooms: 0`으로 표기 후 제외하지 않음

### 예시 2. 부분 실패

B가 타임아웃. A 결과만으로 응답

```json
{
  "checkIn": "2026-10-01",
  "checkOut": "2026-10-03",
  "nights": 2,
  "adults": 2,
  "children": 0,
  "suppliers": [
    { "supplier": "A", "status": "OK" },
    { "supplier": "B", "status": "FAILED", "reason": "TIMEOUT" }
  ],
  "items": [
    { "propertyId": 1, "supplier": "A", "...": "..." },
    { "propertyId": 2, "supplier": "A", "...": "..." }
  ]
}
```

### 예시 3. 전부 실패

HTTP 200. 빈 결과와 실패 사유

```json
{
  "checkIn": "2026-10-01",
  "checkOut": "2026-10-03",
  "nights": 2,
  "adults": 2,
  "children": 0,
  "suppliers": [
    { "supplier": "A", "status": "FAILED", "reason": "UNAVAILABLE" },
    { "supplier": "B", "status": "FAILED", "reason": "NO_MAPPING" }
  ],
  "items": []
}
```

### 예시 4. 잘못된 요청

HTTP 400. 공급사 호출 전에 거절

```json
{
  "error": "INVALID_REQUEST",
  "message": "checkOut must be after checkIn"
}
```

## Mock 제어 (참고)

`mock-supplier`(9090)의 고장 모드 전환

공급사 장애, 무응답 상황을 만들어 위 검색 API의 부분 실패 응답을 확인할 때 사용

```
POST http://localhost:9090/control/{a|b}/mode?value={normal|error|no-response|delay}
```

## 관련

- 표준 모델 필드: [domain-model.md](domain-model.md)
- 부분 실패 표현: [ADR 0006](adr/0006-partial-failure-response.md)

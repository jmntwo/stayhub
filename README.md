## Stayhub
여러 공급사의 서로 다른 API를 하나의 표준 모델로 구성해, 통합 검색을 제공하는 **연동 백엔드**


### 기술 스택

| 항목 | 선택 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.8 (Spring MVC) |
| Build | Gradle, Kotlin DSL, 멀티모듈 |
| HTTP Client | Spring WebClient |
| DB | H2 (JPA) |
| API 문서 | SpringDoc OpenAPI |

### 프로젝트 구조

```
stayhub/
├── app/             통합 검색 서비스 (8080)
├── mock-supplier/   공급사 A·B Mock 서버 (9090)
├── docs/            설계 문서, ADR
├── README.md
└── JOURNAL.md       진행 기록, AI 활용 기록
```

### 빌드 및 실행

JDK 21 필요. 그 외 설치할 것 없음 (DB는 인메모리 H2)

```bash
# 1. 공급사 Mock (9090). 먼저 띄움. 앱이 뜨면서 이 서버의 숙소 목록 API로 매핑을 만듦
./gradlew :mock-supplier:bootRun

# 2. 통합 검색 서비스 (8080)
./gradlew :app:bootRun

# 테스트 전체 (외부 프로세스 없음)
./gradlew test
```

| 확인 | 주소 |
|---|---|
| 통합 검색 | `http://localhost:8080/api/v1/stays/search?checkIn=2026-10-01&checkOut=2026-10-03&adults=2&children=0` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| H2 콘솔 (매핑 테이블 확인) | `http://localhost:8080/h2-console` · JDBC URL `jdbc:h2:mem:stayhub`, 사용자 `sa`, 비밀번호 없음 |
| 상태 | `http://localhost:8080/actuator/health` |

#### 공급사 장애 재현

Mock의 고장 스위치를 켜고 검색하면 응답의 `suppliers`에 실패한 공급사와 사유가 표시되고 나머지 공급사 결과는 그대로 옴

```bash
# B의 재고·요금 API를 장애 상태로
curl -X POST 'http://localhost:9090/control/b/availability/mode?value=error'

# 검색. A 결과만 오고 B는 FAILED + UNAVAILABLE
#   "suppliers": [{"supplier":"A","status":"OK"}, {"supplier":"B","status":"FAILED","reason":"UNAVAILABLE"}]
curl 'http://localhost:8080/api/v1/stays/search?checkIn=2026-10-01&checkOut=2026-10-03&adults=2&children=0'

# B를 무응답 상태로. 검색하면 약 3초 뒤 B는 FAILED + TIMEOUT
curl -X POST 'http://localhost:9090/control/b/availability/mode?value=no-response'

# B 복귀
curl -X POST 'http://localhost:9090/control/b/availability/mode?value=normal'
```

**매핑 동기화 실패 재현**

목록 API는 앱이 뜰 때와 6시간 주기로만 호출하므로, 스위치를 켠 뒤 app을 재시작해야 함

```bash
# A의 숙소 목록 API를 장애 상태로
curl -X POST 'http://localhost:9090/control/a/catalog/mode?value=error'

# app 재시작 (Ctrl+C 후 다시 bootRun). 로그에 A는 실패, B는 성공, 앱은 정상 기동
#   mapping sync failed supplier=A reason=UNAVAILABLE nextAttemptAt=...
#   mapping sync ok supplier=B properties=2 roomTypes=2
#   Started StayhubApplication

# 검색. A는 매핑이 없어 호출 없이 NO_MAPPING, B 상품만 옴
curl 'http://localhost:8080/api/v1/stays/search?checkIn=2026-10-01&checkOut=2026-10-03&adults=2&children=0'

# A 복귀. 재시도는 첫 실패 후 30~60초 뒤 시작해 간격이 두 배씩 늘어남(최대 5분)
curl -X POST 'http://localhost:9090/control/a/catalog/mode?value=normal'
```

스위치는 `{a|b}` × `{catalog|availability}` × `{normal|error|no-response|delay}`. `catalog`를 고장내면 매핑 동기화 실패(`NO_MAPPING`)를 재현. 상세는 [docs/api-spec.md](docs/api-spec.md)

macOS에서 app 기동 시 `Unable to load io.netty.resolver.dns.macos...` ERROR 로그 한 줄이 뜸. netty가 macOS 전용 DNS 라이브러리를 찾지 못해 시스템 기본으로 대신한다는 알림이며 동작과 무관

공급사 API 키는 `app/src/main/resources/application.yaml`의 `stayhub.suppliers.{a,b}.api-key`.
Mock 연동용 고정 값이며, 실제 공급사 연동 시 환경 변수 `STAYHUB_SUPPLIERS_A_API_KEY` 등으로 주입

---

### 설계 결정

결정 하나당 ADR 한 편. 배경, 폐기한 대안, 결정, 비용, AI 제안과 내 판단 순

| # | 결정                                                    | ADR |
|---|-------------------------------------------------------|---|
| 1 | Gradle 멀티모듈. app과 mock-supplier는 서로 의존하지 않음           | [0001](docs/adr/0001-gradle-multi-module.md) |
| 2 | Mock은 런타임용 별도 모듈 + 테스트용 WireMock. 고장 스위치는 (공급사, API)별 | [0002](docs/adr/0002-mock-supplier.md) |
| 3 | Spring MVC 위에서 WebClient만. 리액티브는 조합 지점까지, block은 한 번  | [0003](docs/adr/0003-mvc-with-webclient.md) |
| 4 | 매핑은 H2 인메모리. 기동 시 1회 + 주기 갱신, 실패 시 백오프 재시도            | [0004](docs/adr/0004-mapping-store-and-sync.md) |
| 5 | 요금은 기간 총액 gross, 재고는 최솟값, 재고 0도 노출, 동일 숙소 미병합         | [0005](docs/adr/0005-standard-stay-model.md) |
| 6 | 공급사 실패는 전부 HTTP 200 + `suppliers[]`에 상태 및 사유          | [0006](docs/adr/0006-partial-failure-response.md) |
| 7 | 어댑터는 형식만 정규화, 내부 식별자 결합은 검색 서비스                       | [0007](docs/adr/0007-adapter-responsibility.md) |
| 8 | 실패 판정은 예외 하나 + 사유 enum                                | [0008](docs/adr/0008-failure-normalization.md) |
| 9 | 타임아웃 연결 1초, 응답 3초, 공급사 데드라인 5초 (재시도, 서킷은 설계만)         | [0009](docs/adr/0009-resilience-settings.md) |

#### 표준 모델: 무엇을 표준으로 삼고 무엇을 버렸나

A는 날짜별 단가에 세금 별도, B는 기간 총액에 세금 포함. 둘이 모두 만들 수 있는 값만 표준으로 삼음

| 항목 | 표준 | 버림 | 이유 |
|---|---|---|---|
| 요금 | 요청 기간 총액, 세금 포함 | A의 날짜별 단가는 선택 필드 `nightly`로 격하 | B가 만들 수 없는 값은 표준이 될 수 없음 |
| 재고 | 숙박일 전체 remainingRooms의 최솟값 | 날짜별 재고 상세 | 하루라도 없으면 그 기간을 예약할 수 없음 |
| 동일 숙소 | 공급사별로 각각 노출 | 병합 | 공통 키가 없어 추정으로 합치면 다른 숙소를 같은 상품으로 보여줄 위험. 조식 조건도 달라 같은 상품이 아님 |

상세는 [docs/domain-model.md](docs/domain-model.md), 근거는 [ADR 0005](docs/adr/0005-standard-stay-model.md)

#### 예약 불가 상품은 0으로 노출

`availableRooms: 0`으로 응답에 포함하고 제외하지 않음. 매진과 "공급사 장애로 조회 실패"를 고객이 구분할 수 있고, 결과 개수가 재고에 따라 흔들리지 않음

---

### 문서

| 문서 | 내용 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 구성도, 패키지 경계, 검색 흐름, 매핑 동기화, 숙소 수천 개 확장 설계, 신규 Supplier 추가 절차 |
| [docs/domain-model.md](docs/domain-model.md) | 표준 모델 필드, 공급사별 필드 대응표, 매핑 테이블 스키마 |
| [docs/api-spec.md](docs/api-spec.md) | 검색 API 요청·응답, 부분 실패 예시, Mock 고장 스위치 |
| [docs/adr/](docs/adr/) | 설계 결정 기록 9편 |
| [JOURNAL.md](JOURNAL.md) | 일자별 진행 기록. **AI 활용 기록은 이 파일에 모아둠** |

---

### 구현 범위

| 항목 | 상태 | 비고 |
|---|---|---|
| 표준 숙박 상품 모델 | 구현 | ADR 0005 |
| Supplier 어댑터 A, B | 구현 | 목록 API와 재고 API 호출, 정규화, 실패 판정 |
| 매핑 동기화 | 구현 | 기동 시 1회와 주기 갱신, 실패 시 백오프 재시도, upsert, 비활성화 |
| 통합 검색 API | 구현 | 청크 분할, 공급사별 병렬, 식별자 결합, 병합 |
| 병렬 호출, 타임아웃, 부분 실패, 실패 판정 통일 | 구현 | 견고성 필수 4가지. 통합 테스트로 확인 |
| Mock Supplier | 구현 | 정상, 장애, 무응답, 지연 모드. API별 스위치 |
| Swagger | 구현 | |
| 숙소 수천 개 확장 | 설계만 | architecture.md 5절 |
| 재시도 정책 | 설계만 | ADR 0009. 지금 타임아웃 값으로는 효과가 없어 값 조정과 함께 가야 함 |
| 서킷 브레이커 | 설계만 | ADR 0009 |
| 가상 스레드 | 설계만 | ADR 0009. 검증 후 적용 |
| 연동 지표 | 미구현 | Actuator 의존성만. 공급사별 성공률과 지연은 SupplierOutcome에 재료가 있음 |
| 요금과 재고 캐시, 중복 상품 병합, 통화 처리, 예약 대행 | 미구현 | 선택 항목 |

---

### 테스트

`./gradlew test`로 전부 실행. 외부 프로세스 없음. 68개

| 층 | 무엇을 확인                                                              | 방법 | 위치 |
|---|---------------------------------------------------------------------|---|---|
| 정규화 단위 | A·B 응답 → 표준 형식 계산 (총액 합산, 재고 최솟값, 날짜 누락) → 0                        | DTO를 직접 만들어 넣음 | `supplier/a`, `supplier/b`의 `*NormalizerTest` |
| 어댑터 연동 | HTTP 503 / 400 / 타임아웃 / 연결 끊김 / 깨진 JSON이 SupplierException 사유로 바뀌는지 | WireMock이 공급사 역할 | `supplier/a`, `supplier/b`의 `*AdapterTest` |
| 매핑 저장 | 유니크 제약, 같은 코드 → 같은 id인지, 비활성화                                       | @DataJpaTest + H2 | `mapping/MappingRepositoryTest` |
| 매핑 동기화 | upsert, 사라진 상품 비활성화, 실패 시 기존 유지                                     | 가짜 어댑터 | `mapping/MappingSyncServiceTest` |
| 재시도 판정 | 백오프 간격, 횟수 상한                                                       | 순수 단위 | `mapping/SyncStatusTest` |
| 검색 서비스 | 병합, 부분 실패, NO_MAPPING, 청크 분할, 매핑 없는 코드 무시                           | 가짜 어댑터 | `application/StaySearchServiceTest` |
| 검색 API | 400 처리, 응답 JSON 구조                                                  | @WebMvcTest, 서비스는 가짜 | `api/StaySearchControllerTest` |
| 전체 관통 | 앱을 통째로 띄우고 검색 요청을 보냄. B가 503, E503, 무응답일 때 각각 A 결과만으로 200 응답하고 B는 UNAVAILABLE 또는 TIMEOUT으로 표시되는지. 둘 다 실패면 빈 결과에 사유가 붙는지 | 앱 전체 + WireMock이 A, B 역할 | `SearchIntegrationTest` |

원칙

- 계산은 HTTP 없이, 연동은 실제 HTTP로
- 자동 테스트의 공급사는 WireMock. mock-supplier 모듈은 사람이 띄워 눌러보는 용도 (ADR 0002)
- 무응답 케이스는 테스트에서 응답 타임아웃을 500ms로 줄여 몇 초씩 기다리지 않음
- 손으로 확인하려면 위 "빌드 및 실행"의 장애 재현 절차

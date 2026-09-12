# 아키텍처

## 1. 구성

`app`은 통합 검색 서비스, `mock-supplier`는 공급사 A·B를 흉내내는 서버

```mermaid
flowchart LR
    Client[클라이언트]:::oos

    subgraph app [app :8080]
        direction LR
        API[검색 API] --> SVC[검색 서비스]
        SYNC[매핑 동기화]
        SVC --> DB[(H2 매핑 테이블)]
        SYNC --> DB
        subgraph supplier [supplier 어댑터]
            A[A 어댑터]
            B[B 어댑터]
        end
        SVC -->|재고·요금| A
        SVC --> B
        SYNC -->|숙소 목록| A
        SYNC --> B
    end

    subgraph mock [mock-supplier :9090]
        MA[Supplier A Mock]
        MB[Supplier B Mock]
    end

    Real[실제 공급사 API]:::oos

    Client --> API
    A -->|WebClient| MA
    B -->|WebClient| MB
    MA -.-> Real
    MB -.-> Real

    classDef oos fill:#eee,stroke:#999,color:#666
```



## 2. app 계층과 패키지 경계

```
com.stayhub
├── api          검색 컨트롤러, 요청/응답 DTO
├── application  검색 서비스
├── domain       표준 숙박 상품 모델 (공급사를 모르는 상태)
├── supplier     공급사 연동
│   ├── SupplierAdapter (인터페이스)
│   ├── a        A 어댑터, A 응답 DTO, A → 표준 모델 변환
│   └── b        B 어댑터, B 응답 DTO, B → 표준 모델 변환
└── mapping      매핑 엔티티, 리포지토리, 동기화 스케줄러
```

경계 규칙

- 공급사 응답 DTO는 어댑터가 표준 모델로 바꿔서 내보냄
- `application`은 `SupplierAdapter` 인터페이스만 보기 때문에 A인지 B인지 분기하지 않음
- 공급사별 실패(A의 HTTP 상태 코드, B의 resultCode)는 어댑터 안에서 공통 예외로 바뀜

## 3. 검색 흐름

```mermaid
flowchart TB
    S0["⓪ 사전: 공급사 목록 API → 매핑 테이블 저장"]:::pre
    S1["① 검색 요청"]
    S2["② 매핑 테이블에서<br/>활성 숙소 코드 조회"]
    S3["③ 공급사별로<br/>50개씩 청크 분할"]
    S4A["④ A 어댑터<br/>청크별 병렬 호출"]
    S4B["④ B 어댑터<br/>청크별 병렬 호출"]
    S5A["⑤ A 응답 → 표준 모델<br/>세금 별도 → 합산"]
    S5B["⑤ B 응답 → 표준 모델<br/>resultCode 검사 → 총액"]
    S6["⑥ 병합"]
    S7["⑦ 응답"]
    F["타임아웃 및 장애<br/>→ 공통 예외"]:::fail

    S0 -.-> S2
    S1 --> S2 --> S3
    S3 --> S4A & S4B
    S4A --> S5A --> S6
    S4B --> S5B --> S6
    S4A -.-> F
    S4B -.-> F
    F -.-> S6
    S6 --> S7

    classDef pre fill:#eef,stroke:#88a,color:#446
    classDef fail fill:#fee,stroke:#c66,color:#633
```


## 4. 매핑 동기화

- 시점: 기동 시 매핑이 비어 있으면 즉시 1회, 이후 주기 갱신 (공급사별로 독립 수행)
- 처리: 어댑터로 목록 API 호출 → 같은 (공급사, 코드)는 같은 내부 식별자를 유지하며 upsert → 목록에서 사라진 상품은 비활성화
- 실패
  - 기본: 기존 매핑 유지
  - 비어 있으면 짧은 간격 재시도
  - 매핑이 없는 공급사는 검색 응답에 상태로 표기
- 매핑 키: 숙소는 (공급사, 숙소 코드), 객실 타입은 (공급사, 숙소 코드, 객실 타입 코드)


## 5. 신규 Supplier 추가 절차

1. `supplier.c` 패키지 생성
2. 응답 DTO, `SupplierAdapter` 구현체, C → 표준 모델 변환
3. C의 실패 표현을 공통 예외로 바꾸는 규칙을 어댑터 안에 작성
4. 설정에 C의 base URL, API 키, 타임아웃 추가
5. `mock-supplier`에 C 엔드포인트와 응답 데이터 추가

## 관련 ADR

- [0001 Gradle 멀티모듈 구조](adr/0001-gradle-multi-module.md)
- [0002 Mock Supplier 구성 방식](adr/0002-mock-supplier.md)
- [0003 Spring MVC 위에서 WebClient만 사용](adr/0003-mvc-with-webclient.md)
- [0004 매핑 저장소와 동기화 전략](adr/0004-mapping-store-and-sync.md)

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

<!-- TODO: 멀티모듈 전환 후 채움 -->

```bash
# 예정
```

---

### 설계 결정

결정 하나당 ADR 한 편. 상세는 [docs/adr](docs/adr/) 참고.

### 문서

예정

### 구현 범위

예정

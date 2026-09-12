# JOURNAL

각 Day는 수행 내용, 의사결정, AI 활용, 막힌 점으로 구성

---

## Day 1. 요구사항 분석, 구조 및 컨벤션 결정
#### 2026-09-12 (토)

### 수행 내용

- 요구사항 정리: 필수 구현 6개(표준 모델, 어댑터, 통합 검색, 견고성, Mock, 설계 문서)와 선택 구현 구분
- 저장소 구조, 커밋 및 브랜치 컨벤션, 문서 구성 결정
- Spring Boot 4.0.8 기준 라이브러리 호환 확인 (WebClient 스타터, Resilience4j, SpringDoc)
- Gradle 멀티모듈 전환 (app / mock-supplier), Kotlin DSL, 패키지 com.stayhub
- app 연동 의존성 추가 (WebClient, Actuator, SpringDoc, H2 콘솔)
- H2 인메모리 전환
- ADR 0001~0004 작성

### 의사결정

- Gradle 멀티모듈 (app / mock-supplier) → ADR 0001
- Mock: 런타임은 별도 모듈, 테스트는 WireMock → ADR 0002
- Spring MVC 위에서 WebClient만 사용, WebFlux 미도입 → ADR 0003
- 매핑 저장소는 H2 인메모리, 동기화는 기동 시 1회 + 주기 갱신 → ADR 0004

### AI 활용

| 물음 | 답 | 판단                                |
|---|---|-----------------------------------|
| Mock 구성 방식 3가지의 차이 | 런타임은 별도 모듈, 테스트는 WireMock | 수용. 포트 분리 제약과 용도 차이가 타당           |
| MySQL vs H2 파일 vs H2 인메모리 | 인메모리 추천. 실패 상황별 비교표 제시 | 파일 잔존, 운영 가정, 실패 전제를 차례로 되물은 뒤 수용 |
| ADR 초안 작성 | 5절 구성으로 초안 | 전부 다시 확인해서 읽기 쉽고 간결하게 수정          |

### 막힌 점

- 없음

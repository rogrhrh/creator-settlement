# 설계 결정 사항 (Architecture Decision Records)

---

## ADR-001: H2 in-memory DB 선택

**결정**: H2 in-memory DB 사용

**이유**
- 과제 허용 DB 목록(H2/MySQL/PostgreSQL) 중 별도 설치 없이 실행 가능
- 평가자가 `./gradlew bootRun` 한 번으로 바로 실행 가능
- Spring Boot와 기본 통합 지원

**트레이드오프**
- 서버 재시작 시 데이터 초기화 (DataLoader로 샘플 데이터 자동 삽입)
- 운영 환경에서는 MySQL/PostgreSQL로 교체 필요

**확장 경로**: `application.yaml`의 datasource만 교체하면 MySQL/PostgreSQL 전환 가능. H2 전용 SQL 함수를 사용하지 않아 이식성 유지.

---

## ADR-002: 엔티티 ID에 String 타입 사용

**결정**: 모든 엔티티 PK를 String으로 설정

**이유**
- 과제 샘플 데이터가 `"creator-1"`, `"sale-1"` 형태의 문자열 ID 사용
- DataLoader 시드 데이터와 테스트 코드에서 ID를 직접 지정 가능
- 평가자가 API 응답에서 샘플 데이터 ID와 직접 대조 가능

**트레이드오프**
- 자동 증가 Long ID 대비 인덱스 효율 낮음
- 운영 시스템에서는 UUID나 Long 자동 증가 권장

---

## ADR-003: 금액 타입에 Long 사용

**결정**: `amount`, `refundAmount`를 `Long` 타입으로 처리

**이유**
- 금액이 원 단위 정수이므로 부동소수점 오류 불필요
- `BigDecimal` 대비 연산 단순, 직렬화 직관적
- 과제 샘플 데이터가 모두 정수 금액 사용

**가정**: 본 과제의 금액은 원 단위 정수로 가정합니다. 소수점 단위 통화나 환율 계산이 필요한 경우 `BigDecimal`로 확장할 수 있습니다.

---

## ADR-004: 타임스탬프에 OffsetDateTime 사용, 월 조회에 YearMonth 사용

**결정**
- `paidAt`, `canceledAt`: `OffsetDateTime`
- 월별 조회 파라미터: `YearMonth`

**이유**
- `OffsetDateTime`은 KST offset(`+09:00`)을 포함한 시점 정보를 표현할 수 있어 KST 기준 구간 계산에 적합
- DB 내부 저장 방식(UTC 변환 여부 등)에는 의존하지 않으며, JPA가 처리하도록 위임
- KST 기준 `[start, end)` 구간은 Java에서 계산 후 JPQL 파라미터로 전달 → DB 함수 불필요
- `Instant` 대비 사람이 읽기 쉬운 형식
- `YearMonth`는 연/월 파싱에 타입 안전성 제공
- H2/JPA와 `OffsetDateTime` 호환성 확인됨

---

## ADR-005: KST 월 경계를 반열린 구간(startInclusive, endExclusive)으로 처리

**결정**: `paidAt >= :start AND paidAt < :end` 방식 사용

**이유**
- `23:59:59` 마지막 초 포함/제외 경계 오류 방지
- 연속 구간 계산 시 `end = 다음달 start`로 단순화
- 과제 명세(assignment.md §8.3)에서 권장하는 방식

**구현 예시**
```
2025-03 조회:
  start = 2025-03-01T00:00:00+09:00
  end   = 2025-04-01T00:00:00+09:00
```

---

## ADR-006: Header 기반 Mock 인증 사용 (Spring Security 미사용)

**결정**: `X-User-Id`, `X-User-Role` 헤더를 Controller에서 직접 파싱

**이유**
- 과제에서 인증/인가 간략화 명시적 허용
- JWT, Spring Security 추가 시 구현 복잡도 대비 평가 가치 낮음
- 핵심 평가 기준(정산 정확성, 테스트)에 집중
- 평가자가 curl/HTTP 클라이언트로 쉽게 테스트 가능

**규칙**
- `X-User-Role: CREATOR` + `X-User-Id == creatorId` → 본인 정산 조회 허용
- `X-User-Role: ADMIN` → 운영자 집계 API 접근 허용
- 불일치 → 403 Forbidden

---

## ADR-007: 판매와 환불의 월 귀속 기준 분리

**결정**
- 판매 → `paidAt` 기준 월에 귀속
- 환불 → `canceledAt` 기준 월에 귀속 (판매 월과 무관)

**이유**
- 과제 시나리오 12.3: sale-5는 1월 판매, cancel-3은 2월 환불 → 각 월에 독립적으로 반영
- 취소가 발생한 시점 기준으로 해당 월 정산에서 차감하는 것이 비즈니스적으로 자연스러움

---

## ADR-008: 선택 구현 범위 제한

**결정**: Phase 1~4(필수 구현) 완료 후 선택 구현 여부를 검토한다

- 현재 구현 엔티티: `Creator`, `Course`, `SaleRecord`, `RefundRecord` 4개만 사용
- `SettlementRecord`는 이번 단계에서 도입하지 않음
- 선택 구현 검토 순서: ① 중복 정산 방지 → ② 정산 상태 관리

**이유**
- 수요일 마감 내 정산 정확성·테스트 품질 우선
- 수수료율 이력, CSV는 `FeePolicy` 클래스 분리로 확장 가능성만 확보
- 과도한 선택 구현보다 필수 구현의 완성도가 채용 평가에서 중요

**향후 개선**: 수수료율 이력 관리, CSV 다운로드, 정산 상태 관리는 README 향후 개선 항목으로 명시

---

## ADR-009: DB 이식성을 위한 JPQL 전용 쿼리 사용

**결정**: H2 전용 SQL 함수, native query, DB 날짜 함수 미사용

**이유**
- `MONTH()`, `YEAR()`, `DATE_TRUNC()` 등은 DB별 호환성 문제 발생
- Java에서 KST 기준 `[start, end)` 구간 계산 후 JPQL 파라미터로 전달하면 모든 DB 호환
- 향후 MySQL/PostgreSQL 전환 시 쿼리 변경 불필요

---

## ADR-010: 정산 계산 로직을 SettlementCalculator로 분리

**결정**: 계산 로직을 독립된 `SettlementCalculator` 클래스에 위치

**이유**
- Controller, Repository에 계산 로직이 혼재되면 테스트 어려움
- 순수 Java 메서드로 분리 시 단위 테스트 용이
- 수수료율 변경 시 `FeePolicy`만 수정하면 되는 구조

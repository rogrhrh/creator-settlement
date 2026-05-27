# 크리에이터 정산 API

## 프로젝트 개요

크리에이터가 강의를 판매하면 플랫폼 수수료를 제외한 금액을 월별로 정산하는 API입니다.
KST 기준 월 경계 처리, 환불 누적 검증, 정산 계산 정확성에 집중하여 구현했습니다.

필수 기능(판매·환불 등록, 월별 정산, 운영자 집계)과 선택 기능(정산 상태 관리, 중복 정산 방지, CSV 다운로드, 수수료율 이력 관리) 모두 구현했습니다.

## 기술 스택

| 항목 | 선택 |
|------|------|
| Framework | Spring Boot 4.0.6 |
| Language | Java 21 |
| ORM | Spring Data JPA (Hibernate 7) |
| DB | H2 in-memory |
| 빌드 | Gradle |
| 테스트 | JUnit 5, MockMvc |

## 실행 방법

```bash
./gradlew bootRun
```

- 서버: http://localhost:8080
- H2 콘솔: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:settlementdb`
  - 애플리케이션 시작 시 샘플 데이터 자동 삽입

## 테스트 실행 방법

```bash
./gradlew test
```

```bash
# 특정 테스트만 실행
./gradlew test --tests "com.ahn.settlement.integration.SettlementFlowIntegrationTest"
```

## API 목록 및 예시

### 판매 내역 등록

```
POST /api/sales
Content-Type: application/json

{
  "id": "sale-1",
  "courseId": "course-1",
  "studentId": "student-1",
  "amount": 50000,
  "paidAt": "2025-03-05T10:00:00+09:00"
}
```

### 환불 내역 등록

```
POST /api/refunds
Content-Type: application/json

{
  "id": "cancel-1",
  "saleRecordId": "sale-3",
  "refundAmount": 80000,
  "canceledAt": "2025-03-21T10:00:00+09:00"
}
```

### 판매 내역 조회

```
GET /api/sales?creatorId=creator-1&startDate=2025-03-01&endDate=2025-03-31
```

### 크리에이터 월별 정산 조회

```
GET /api/settlements/creators/creator-1?yearMonth=2025-03
X-User-Id: creator-1
X-User-Role: CREATOR

응답:
{
  "creatorId": "creator-1",
  "yearMonth": "2025-03",
  "totalSalesAmount": 260000,
  "totalRefundAmount": 110000,
  "netSalesAmount": 150000,
  "platformFee": 30000,
  "payoutAmount": 120000,
  "saleCount": 4,
  "cancelCount": 2
}
```

### 운영자 기간별 집계

```
GET /api/settlements/admin?startDate=2025-03-01&endDate=2025-03-31
X-User-Role: ADMIN

응답:
{
  "startDate": "2025-03-01",
  "endDate": "2025-03-31",
  "creatorSettlements": [
    {
      "creatorId": "creator-1",
      "creatorName": "김강사",
      "totalSalesAmount": 260000,
      "totalRefundAmount": 110000,
      "netSalesAmount": 150000,
      "platformFee": 30000,
      "payoutAmount": 120000
    }
  ],
  "totalPayoutAmount": 120000
}
```

### 정산 확정 (ADMIN)

```
POST /api/settlements
X-User-Role: ADMIN
Content-Type: application/json

{
  "creatorId": "creator-1",
  "yearMonth": "2025-03"
}

→ 201 Created, SettlementRecord 생성 (상태: PENDING)
```

```
PATCH /api/settlements/{id}/confirm
X-User-Role: ADMIN
→ PENDING → CONFIRMED

PATCH /api/settlements/{id}/pay
X-User-Role: ADMIN
→ CONFIRMED → PAID
```

### 정산 이력 조회 및 CSV 다운로드

```
GET /api/settlements/creators/creator-1/history
X-User-Id: creator-1
X-User-Role: CREATOR

GET /api/settlements/creators/creator-1/history/csv
X-User-Id: creator-1
X-User-Role: CREATOR
→ Content-Type: text/csv, 파일명 settlement_creator-1.csv
```

### 수수료율 관리 (ADMIN)

```
POST /api/fee-policies
X-User-Role: ADMIN
Content-Type: application/json

{
  "ratePercent": 15,
  "effectiveFrom": "2025-06-01"
}
→ 201 Created. effectiveFrom 중복 시 409 Conflict.

GET /api/fee-policies
X-User-Role: ADMIN
→ effectiveFrom 내림차순 이력 목록
```

## 정산 계산식

```
순매출 = 총 판매금액 - 총 환불금액
플랫폼 수수료 = 순매출 × 수수료율 / 100
정산 예정 금액 = 순매출 - 플랫폼 수수료
```

수수료율은 `fee_policy_history` 테이블에서 관리됩니다. 정산 기준 월 1일 기준으로 `effective_from <= 기준일` 조건의 가장 최신 수수료율이 적용되어, 수수료율 변경이 과거 정산에 영향을 주지 않습니다.

초기 시드: 20% (2020-01-01~)

## 인증 방식 (Mock)

| 헤더 | 설명 |
|------|------|
| `X-User-Id` | 사용자 식별자 |
| `X-User-Role` | `CREATOR` 또는 `ADMIN` |

- `CREATOR`: `X-User-Id == creatorId` 일치 시 본인 정산만 조회 가능, 불일치 시 403
- `ADMIN`: 운영자 집계 API 접근 권한, 크리에이터 정산도 조회 가능

## 데이터 모델

```
Creator(id, name)
  └──< Course(id, creatorId, title)
          └──< SaleRecord(id, courseId, studentId, amount, paidAt)
                  └──< RefundRecord(id, saleRecordId, refundAmount, canceledAt)
                  └──< SettlementRecord(id, creatorId, yearMonth, netSalesAmount,
                                        feeRatePercent, platformFee, payoutAmount,
                                        status[PENDING→CONFIRMED→PAID])

FeePolicyHistory(id, ratePercent, effectiveFrom)  ← UNIQUE(effectiveFrom)
```

- `amount`, `refundAmount`: 원 단위 정수 (Long)
- `paidAt`, `canceledAt`: OffsetDateTime. KST 기준 구간을 Java에서 계산해 JPQL 파라미터로 전달.

## 요구사항 해석 및 가정

| 항목 | 결정 |
|------|------|
| 판매 귀속 기준 | `paidAt` 기준 월 |
| 환불 귀속 기준 | `canceledAt` 기준 월 (판매 월과 독립) |
| 월 경계 방식 | `[start, end)` 반열린 구간 — 마지막 초 포함/제외 오류 방지 |
| 빈 월 조회 | 판매 없어도 0원 응답 (404 아님) |
| 금액 단위 | 원 단위 정수 가정 |
| 수수료율 | DB 테이블(`fee_policy_history`) 관리 — 기준 월 1일 기준 유효 rate 적용 |

## 설계 결정과 이유

| 결정 | 이유 |
|------|------|
| String ID | 샘플 데이터(creator-1 등)와 일치, 테스트 코드 가독성 향상 |
| Long 금액 | 원 단위 정수 → 부동소수점 오류 없음 |
| OffsetDateTime | KST offset 정보 보존, DB 저장 방식에 무관 |
| Java에서 KST 구간 계산 | DB 함수(MONTH, DATE_TRUNC 등) 미사용 → H2/MySQL/PostgreSQL 모두 호환 |
| Header mock 인증 | 과제 허용 사항, 핵심 평가(정산 정확성, 테스트)에 집중 |
| SettlementRecord 별도 저장 | 확정 시점 수수료율 고정 — 이후 rate 변경 영향 없음 |
| 수수료율 DB 관리 | 변경 이력 보존, `effectiveFrom` 기준 point-in-time 조회 |

## 필수 시나리오 검증

| 시나리오 | 결과 | 검증 테스트 |
|----------|------|------------|
| creator-1 / 2025-03 정산 | payoutAmount: 120,000원 | `SettlementIntegrationTest`, `SettlementFlowIntegrationTest` |
| 부분 환불 (sale-4 / cancel-2) | 순매출에 50,000원 잔존 | `SettlementIntegrationTest` |
| 월 경계 취소 (sale-5 / cancel-3) | 판매→1월, 환불→2월 독립 귀속 | `SettlementIntegrationTest`, `SettlementFlowIntegrationTest` |
| 빈 월 조회 (creator-3 / 2025-03) | 모든 값 0, 에러 없음 | `SettlementIntegrationTest` |
| 수수료율 변경 후 이전 월 정산 | 구 수수료율 그대로 적용 | `FeePolicyIntegrationTest` |
| 수수료율 변경 후 이후 월 정산 | 신 수수료율 적용 | `FeePolicyIntegrationTest` |
| 전체 플로우 (판매→환불→정산→admin 집계) | 각 단계 일관성 확인 | `SettlementFlowIntegrationTest` |

## AI 활용 범위

### 사용 도구

**Claude Code** (claude-sonnet-4-6) + **Superpowers** 플러그인을 사용했습니다.
Superpowers는 Claude Code 위에서 동작하는 스킬 기반 개발 워크플로우 도구입니다.

### 개발 워크플로우

선택 기능 4개 각각에 대해 아래 사이클을 반복했습니다.

```
브레인스토밍 → 설계 문서 → 구현 계획 → 서브에이전트 구현 → 검토/병합
```

**1단계 — 브레인스토밍 (`/brainstorming`)**

요구사항에 대해 Claude와 질문/답변을 주고받으며 설계를 확정했습니다.
예를 들어 수수료율 관리 기능에서는 "하드코딩 vs enum vs DB 테이블" 세 가지 방식의 장단점을 비교 제안받은 뒤 직접 선택했습니다. 날짜 타입(LocalDate vs String), API 설계, 엣지 케이스 처리 방식도 이 단계에서 결정했습니다.
확정된 설계는 `docs/superpowers/specs/` 에 마크다운 문서로 저장됩니다.

**2단계 — 구현 계획 (`/writing-plans`)**

설계 문서를 바탕으로 Claude가 태스크별 구현 계획을 작성합니다.
각 태스크에는 수정할 파일, 작성할 테스트 코드(TDD 순서), 실행 커맨드, 예상 출력까지 포함됩니다.
계획 문서는 `docs/superpowers/plans/` 에 저장됩니다.

**3단계 — 서브에이전트 구현 (`/subagent-driven-development`)**

구현 계획의 태스크를 하나씩 독립된 서브에이전트에게 위임합니다.
서브에이전트는 TDD 방식으로 구현 후 자체 리뷰까지 수행합니다. 이후 두 단계 리뷰가 자동으로 진행됩니다.

- **스펙 준수 리뷰**: 구현이 설계 문서의 요구사항을 모두 충족하는지, 불필요한 기능이 추가되지 않았는지 확인
- **코드 품질 리뷰**: 네이밍, 중복, 불필요한 복잡도 등 코드 품질 검토

리뷰에서 지적이 나오면 같은 서브에이전트가 수정 → 재검토 루프를 돌고, 통과하면 다음 태스크로 넘어갑니다.

### 인간의 역할

AI가 코드를 생성하는 동안 아래 판단은 직접 내렸습니다.

- 기능 구현 여부 및 우선순위 결정
- 브레인스토밍 단계에서 설계 방향 선택 (예: 수수료율 DB 관리 방식, LocalDate 사용 등)
- 설계 문서 검토 및 승인 — 문서 저장 전 내용 확인
- 구현 결과가 의도한 동작을 하는지 테스트 통과 여부로 확인
- 예외 상황 대응: 서브에이전트가 `@Profile("!test")` 어노테이션을 무단 추가하는 등 의도를 벗어난 구현은 직접 발견하고 롤백 지시

### 정리

코드 작성의 대부분을 AI가 담당했지만, 설계 결정과 결과 검증은 모두 직접 수행했습니다.
AI는 "어떻게 구현할까"를 담당했고, "무엇을 왜 만들까"는 인간이 결정했습니다.

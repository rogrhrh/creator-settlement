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
- Swagger UI: http://localhost:8080/swagger-ui/index.html
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

**Claude Code** (claude-sonnet-4-6)를 사용했습니다.

초기 프로젝트 구조 설계부터 선택 기능 구현, 테스트 작성까지 전반적으로 활용했습니다. 각 기능은 요구사항을 대화로 정리해 설계 문서를 먼저 만들고, 그 문서를 기반으로 코드를 생성하는 방식으로 진행했습니다. 기능별로 GitHub 이슈와 PR을 분리해 관리했습니다.

생성된 코드와 테스트는 직접 실행하고 결과를 확인했습니다. 테스트 assertions의 예상값(순매출, 수수료, 지급액)은 샘플 데이터를 바탕으로 직접 계산해 검증했습니다. 의도와 다르게 생성된 코드(불필요한 어노테이션 추가, 테스트 설정 변경 등)는 원인을 파악하고 수정했습니다.

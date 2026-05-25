# 크리에이터 정산 API

## 프로젝트 개요

크리에이터가 강의를 판매하면 플랫폼 수수료를 제외한 금액을 월별로 정산하는 API입니다.
KST 기준 월 경계 처리, 환불 누적 검증, 정산 계산 정확성에 집중하여 구현했습니다.

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
./gradlew test --tests "com.ahn.settlement.integration.SettlementIntegrationTest"
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

## 정산 계산식

```
순매출 = 총 판매금액 - 총 환불금액
플랫폼 수수료 = 순매출 × 20 / 100
정산 예정 금액 = 순매출 - 플랫폼 수수료
```

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
```

- `amount`, `refundAmount`: 원 단위 정수 (Long). 소수점 통화 필요 시 BigDecimal 확장 가능.
- `paidAt`, `canceledAt`: OffsetDateTime. KST 기준 구간을 Java에서 계산해 JPQL 파라미터로 전달.

## 요구사항 해석 및 가정

| 항목 | 결정 |
|------|------|
| 판매 귀속 기준 | `paidAt` 기준 월 |
| 환불 귀속 기준 | `canceledAt` 기준 월 (판매 월과 독립) |
| 월 경계 방식 | `[start, end)` 반열린 구간 — 마지막 초 포함/제외 오류 방지 |
| 빈 월 조회 | 판매 없어도 0원 응답 (404 아님) |
| 금액 단위 | 원 단위 정수 가정 |
| 수수료율 | 20% 고정 (`FeePolicy` 클래스로 분리하여 확장 가능) |

## 설계 결정과 이유

| 결정 | 이유 |
|------|------|
| String ID | 샘플 데이터(creator-1 등)와 일치, 테스트 코드 가독성 향상 |
| Long 금액 | 원 단위 정수 → 부동소수점 오류 없음 |
| OffsetDateTime | KST offset 정보 보존, DB 저장 방식에 무관 |
| Java에서 KST 구간 계산 | DB 함수(MONTH, DATE_TRUNC 등) 미사용 → H2/MySQL/PostgreSQL 모두 호환 |
| Header mock 인증 | 과제 허용 사항, 핵심 평가(정산 정확성, 테스트)에 집중 |
| SettlementRecord 미사용 | 필수 API는 실시간 집계로 충분 |

## 필수 시나리오 검증

| 시나리오 | 결과 | 검증 테스트 |
|----------|------|------------|
| creator-1 / 2025-03 정산 | payoutAmount: 120,000원 | `SettlementIntegrationTest` |
| 부분 환불 (sale-4 / cancel-2) | 순매출에 50,000원 잔존 | `SettlementIntegrationTest` |
| 월 경계 취소 (sale-5 / cancel-3) | 판매→1월, 환불→2월 독립 귀속 | `SettlementIntegrationTest` |
| 빈 월 조회 (creator-3 / 2025-03) | 모든 값 0, 에러 없음 | `SettlementIntegrationTest` |

## 미구현 / 향후 개선 사항

- 정산 상태 관리 (PENDING → CONFIRMED → PAID)
- 중복 정산 방지 (SettlementRecord 추가 시 UNIQUE 제약)
- 수수료율 이력 관리 (FeePolicy DB 테이블화)
- CSV 다운로드

## AI 활용 범위

Claude Code (claude-sonnet-4-6)를 사용하여 설계 문서, 구현 계획, 코드 초안을 생성했습니다.
모든 설계 결정(ID 타입, 금액 타입, KST 처리 방식, 인증 방식, 선택 구현 범위 등)은 직접 검토하고 확정했습니다.
생성된 코드는 실행 테스트 및 필수 시나리오 검증을 통해 확인했습니다.

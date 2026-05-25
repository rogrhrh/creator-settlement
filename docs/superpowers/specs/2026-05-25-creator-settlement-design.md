# Creator Settlement API — 설계 명세
날짜: 2026-05-25

---

## 1. 프로젝트 개요

국내 프로덕트 엔지니어 채용 과제 (BE-B. 크리에이터 정산 API).
크리에이터가 강의를 판매하면 플랫폼 수수료를 제외한 금액을 월별로 정산하는 API를 구현한다.

**핵심 목표**: 정산 계산 정확성, KST 월 경계 처리, 환불 검증, 테스트 가능성

---

## 2. 기술 스택

| 항목 | 선택 |
|------|------|
| Framework | Spring Boot 4.0.6 |
| Language | Java 21 |
| ORM | Spring Data JPA |
| DB | H2 (in-memory) |
| 금액 타입 | Long (원 단위 정수) |
| 시간 타입 | OffsetDateTime |
| 인증 | Header mock (X-User-Id, X-User-Role) |

---

## 3. 도메인 모델

### 엔티티 관계

```
Creator 1──N Course 1──N SaleRecord 1──N RefundRecord
```

### 엔티티 정의

**Creator**
- id: String (PK, e.g. "creator-1")
- name: String

**Course**
- id: String (PK, e.g. "course-1")
- creatorId: String (FK → Creator)
- title: String

**SaleRecord**
- id: String (PK, e.g. "sale-1")
- courseId: String (FK → Course)
- studentId: String
- amount: Long (원 단위)
- paidAt: OffsetDateTime (UTC 저장)

**RefundRecord**
- id: String (PK, e.g. "cancel-1")
- saleRecordId: String (FK → SaleRecord)
- refundAmount: Long (원 단위)
- canceledAt: OffsetDateTime (UTC 저장)

**[Deferred] SettlementRecord** — 중복 정산 방지/상태 관리 구현 시 추가
- creatorId, yearMonth, status (PENDING→CONFIRMED→PAID)
- UNIQUE(creatorId, yearMonth)

---

## 4. API 목록

### 판매/환불

| Method | Path | 설명 | 역할 |
|--------|------|------|------|
| POST | /api/sales | 판매 내역 등록 | ADMIN |
| POST | /api/refunds | 환불 내역 등록 | ADMIN |
| GET | /api/sales | 판매 내역 조회 (creatorId, 기간 필터) | CREATOR/ADMIN |

### 정산

| Method | Path | 설명 | 역할 |
|--------|------|------|------|
| GET | /api/settlements/creators/{creatorId} | 크리에이터 월별 정산 조회 | CREATOR |
| GET | /api/settlements/admin | 운영자 기간별 전체 집계 | ADMIN |

### 헤더 인증 (Mock)

```
X-User-Id:   사용자 식별자
X-User-Role: CREATOR | ADMIN
```

- CREATOR: 본인 creatorId 정산만 조회 가능
- ADMIN: 운영자 집계 API 접근 가능

---

## 5. 정산 계산식

```
totalSalesAmount  = 해당 월 SaleRecord.amount 합계 (paidAt 기준)
totalRefundAmount = 해당 월 RefundRecord.refundAmount 합계 (canceledAt 기준)
netSalesAmount    = totalSalesAmount - totalRefundAmount
platformFee       = netSalesAmount * 20 / 100
payoutAmount      = netSalesAmount - platformFee
```

---

## 6. KST 월 경계 처리

- DB 저장: UTC (OffsetDateTime)
- 월별 조회 파라미터: YearMonth ("2025-03")
- Java에서 KST 기준 구간 계산 후 JPQL에 전달

```
start = YearMonth의 1일 00:00:00 KST → UTC 변환
end   = 다음달 1일 00:00:00 KST → UTC 변환

쿼리: paidAt >= :start AND paidAt < :end
```

- H2/MySQL/PostgreSQL 모두 호환 (DB 날짜 함수 미사용)

---

## 7. 환불 검증 규칙

1. 존재하지 않는 SaleRecord → 404 오류
2. refundAmount ≤ 0 → 400 오류
3. refundAmount > saleRecord.amount → 400 오류
4. 누적 환불 합계 > saleRecord.amount → 400 오류
5. 환불 월 귀속: canceledAt 기준 (paidAt 기준 아님)

---

## 8. 책임 분리

```
controller/
  SaleController        판매 등록, 조회
  RefundController      환불 등록
  SettlementController  월별 정산, 운영자 집계

service/
  SaleService           판매 등록/조회 유스케이스
  RefundService         환불 등록, 누적 검증
  SettlementService     월별 정산, 기간별 집계

domain/
  SettlementCalculator  순매출·수수료·지급액 계산
  FeePolicy             수수료율 20% 상수 (확장 가능 인터페이스 구조)
  SettlementResult      계산 결과 record (불변 값 객체)
  KstDateRange          KST 월 경계 → UTC 구간 변환 유틸

repository/
  SaleRecordRepository
  RefundRecordRepository
  CourseRepository
  CreatorRepository
```

---

## 9. 선택 구현 범위 (이번 MVP)

| 항목 | 포함 여부 |
|------|-----------|
| 중복 정산 방지 | 핵심 완료 후 1순위 |
| 정산 상태 관리 | 시간 여유 시 |
| 수수료율 이력 | README 향후 개선 |
| CSV 다운로드 | README 향후 개선 |

---

## 10. 테스트 전략

필수 시나리오 (sample data 기반 통합 테스트):
- creator-1의 2025-03 정산 → payoutAmount = 120,000원
- 부분 환불 (sale-4 / cancel-2): 순매출에 50,000원 잔존
- 월 경계 취소 (sale-5 / cancel-3): 판매는 1월, 환불은 2월에 각각 귀속
- 빈 월 조회 (creator-3 / 2025-03): 0원 응답, 에러 없음

추가 단위 테스트:
- 환불 금액 초과 → 예외
- 누적 환불 초과 → 예외
- KST 경계 (월 첫날 00:00, 마지막날 23:59:59, 다음달 00:00)
- 존재하지 않는 creatorId/courseId/saleRecordId

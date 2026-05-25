# 요구사항 명세 (Spec)

## 1. 필수 구현 API

### 1.1 판매 내역 등록
`POST /api/sales`

**요청 바디**
```json
{
  "id": "sale-1",
  "courseId": "course-1",
  "studentId": "student-1",
  "amount": 50000,
  "paidAt": "2025-03-05T10:00:00+09:00"
}
```

**검증**
- courseId가 존재해야 한다
- amount > 0

---

### 1.2 환불 내역 등록
`POST /api/refunds`

**요청 바디**
```json
{
  "id": "cancel-1",
  "saleRecordId": "sale-3",
  "refundAmount": 80000,
  "canceledAt": "2025-03-21T10:00:00+09:00"
}
```

**검증**
- saleRecordId가 존재해야 한다
- refundAmount > 0
- refundAmount ≤ 원결제 금액
- 누적 환불 합계 ≤ 원결제 금액

---

### 1.3 판매 내역 조회
`GET /api/sales?creatorId={creatorId}&startDate={date}&endDate={date}`

**응답**
```json
[
  {
    "id": "sale-1",
    "courseId": "course-1",
    "courseTitle": "Spring Boot 입문",
    "studentId": "student-1",
    "amount": 50000,
    "paidAt": "2025-03-05T10:00:00+09:00"
  }
]
```

---

### 1.4 크리에이터 월별 정산 조회
`GET /api/settlements/creators/{creatorId}?yearMonth=2025-03`

헤더: `X-User-Id`, `X-User-Role: CREATOR`

**응답**
```json
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

**빈 월 조회 시** (판매 없음): 모든 금액 0, count 0으로 응답. 404 아님.

---

### 1.5 운영자 기간별 정산 집계
`GET /api/settlements/admin?startDate=2025-03-01&endDate=2025-03-31`

헤더: `X-User-Role: ADMIN`

**날짜 범위 처리**: startDate/endDate는 KST 기준 날짜 (`yyyy-MM-dd`).
- start = `startDate T00:00:00+09:00`
- end   = `endDate 다음날 T00:00:00+09:00` (endExclusive)
- 즉 `2025-03-31` 종료 시 `2025-04-01T00:00:00+09:00` 미만으로 처리

**응답**
```json
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

---

## 2. 정산 계산식

```
totalSalesAmount  = 기간 내 SaleRecord.amount 합계 (paidAt 기준)
totalRefundAmount = 기간 내 RefundRecord.refundAmount 합계 (canceledAt 기준)
netSalesAmount    = totalSalesAmount - totalRefundAmount
platformFee       = netSalesAmount * 20 / 100
payoutAmount      = netSalesAmount - platformFee
```

---

## 3. 정산 기간 기준

| 항목 | 기준 |
|------|------|
| 판매 귀속 | paidAt (KST) |
| 환불 귀속 | canceledAt (KST) — 판매 월과 독립 |
| 구간 방식 | `start <= t < end` (반열린 구간) |
| 타임존 | KST (Asia/Seoul, +09:00) |

2025-03 조회 시:
- start = `2025-03-01T00:00:00+09:00`
- end   = `2025-04-01T00:00:00+09:00`

---

## 4. 필수 검증 시나리오

### 시나리오 1: creator-1의 2025-03 정산

| 항목 | 기대값 |
|------|--------|
| 총 판매 | 260,000원 |
| 환불 | 110,000원 |
| 순 판매 | 150,000원 |
| 수수료 | 30,000원 |
| 정산 예정 | **120,000원** |

### 시나리오 2: 부분 환불 (sale-4 / cancel-2)
- 원결제: 80,000원, 환불: 30,000원 → 잔여: 50,000원

### 시나리오 3: 월 경계 취소 (sale-5 / cancel-3)
- sale-5: 2025-01 판매 귀속
- cancel-3: 2025-02 환불 귀속 (각각 독립)

### 시나리오 4: 빈 월 조회 (creator-3 / 2025-03)
- 모든 값 0으로 응답, 에러 없음

---

## 5. 인증 (Mock)

```
X-User-Id:   사용자 식별자 (예: "creator-1")
X-User-Role: CREATOR | ADMIN
```

- CREATOR: `X-User-Id == creatorId` 조건 만족 시만 조회 가능
- ADMIN: 운영자 집계 API 전용 접근
- 불일치 시 403 반환

---

## 6. 샘플 데이터 (DataLoader로 애플리케이션 시작 시 삽입)

크리에이터 3명, 강의 4개, 판매 7건, 환불 3건 — assignment.md 섹션 11 참조

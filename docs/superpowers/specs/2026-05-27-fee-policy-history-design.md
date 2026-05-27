---
title: 수수료율 변경 이력 관리 설계
date: 2026-05-27
status: approved
---

# 수수료율 변경 이력 관리 설계

## 목표

- 수수료율을 DB 테이블로 관리하여 변경 이력 보존
- 정산 계산 시 `paidAt` 기준 당시 유효 수수료율 적용
- 현재 수수료율 변경이 과거 정산 결과에 영향을 주지 않음
- ADMIN API로 새 수수료율 등록 및 이력 조회 가능

## 데이터 모델

### fee_policy_history 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | String (PK) | 식별자 |
| `rate_percent` | Long | 수수료율 (예: 20 → 20%) |
| `effective_from` | LocalDate | 해당 수수료율 적용 시작일 |

- 같은 `effective_from`에 중복 등록 불가 (UNIQUE 제약)
- 초기 시드: `id = "fee-policy-1"`, `ratePercent = 20`, `effectiveFrom = 2020-01-01`

## 수수료율 조회 로직

정산 계산 시 `targetDate` (KST 기준 해당 월 1일) 기준으로 유효한 수수료율 조회:

```
SELECT * FROM fee_policy_history
WHERE effective_from <= :targetDate
ORDER BY effective_from DESC
LIMIT 1
```

- 조회 결과 없으면 서버 에러 (초기 시드가 항상 존재하므로 실질적으로 발생 안 함)
- `SettlementService` (월별 정산, Admin 집계) 및 `SettlementConfirmService` (정산 확정) 모두 이 방식으로 rate 조회

## API

### 수수료율 등록

```
POST /api/fee-policies
X-User-Role: ADMIN

{
  "ratePercent": 15,
  "effectiveFrom": "2025-06-01"
}
```

- 응답: 201 Created
- `effective_from` 중복 시 409 Conflict
- ADMIN 이외 접근 시 403 Forbidden

### 수수료율 이력 조회

```
GET /api/fee-policies
X-User-Role: ADMIN

응답:
[
  { "id": "fee-policy-1", "ratePercent": 20, "effectiveFrom": "2020-01-01" },
  { "id": "fee-policy-2", "ratePercent": 15, "effectiveFrom": "2025-06-01" }
]
```

- `effective_from` 내림차순 정렬

## 기존 코드 변경

| 대상 | 변경 내용 |
|------|----------|
| `FeePolicy` | 삭제 — DB 조회로 대체 |
| `SettlementCalculator` | 변경 없음 — 이미 `feeRatePercent` 파라미터로 수신 |
| `SettlementService` | rate 조회 후 `SettlementCalculator`에 전달 |
| `SettlementConfirmService` | rate 조회 후 계산 및 `SettlementRecord`에 저장 |
| `DataLoader` | 초기 수수료율 시드 추가 |

## 테스트 시나리오

1. 기본 동작 — 시드 데이터 20% 기준 기존 정산 결과 동일
2. 수수료율 변경 후 이후 날짜 판매에 새 rate 적용
3. 수수료율 변경 전 날짜 판매에 이전 rate 유지
4. 중복 `effective_from` 등록 시 409 반환
5. ADMIN 아닌 역할로 POST 시 403 반환

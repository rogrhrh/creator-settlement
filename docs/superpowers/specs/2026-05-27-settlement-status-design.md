# 정산 확정 상태 관리 설계 명세

작성일: 2026-05-27  
관련 이슈: #3

---

## 배경

현재 정산은 조회 시마다 실시간 계산만 하고 저장하지 않는다.  
정산 결과를 DB에 영속화하고 PENDING → CONFIRMED → PAID 상태 전이를 관리한다.

---

## 아키텍처

### 새 구성요소

| 파일 | 역할 |
|------|------|
| `entity/SettlementRecord.java` | 정산 결과 영속화 |
| `entity/SettlementStatus.java` | 상태 enum (PENDING, CONFIRMED, PAID) |
| `repository/SettlementRecordRepository.java` | 정산 레코드 조회 |
| `service/SettlementConfirmService.java` | 생성·상태 전이 담당 |
| `dto/request/SettlementCreateRequest.java` | 정산 생성 요청 DTO |
| `dto/response/SettlementRecordResponse.java` | 정산 레코드 응답 DTO |

### 기존 변경

- `SettlementService` — `calculateSettlement(creatorId, yearMonth)` 내부 메서드 추출  
  (`SettlementConfirmService`가 재사용, 계산 로직 중복 방지)
- `SettlementController` — 새 엔드포인트 4개 추가

---

## API 명세

모든 쓰기 API는 `X-User-Role: ADMIN` 필요.

### 정산 생성

```
POST /api/settlements
Header: X-User-Role: ADMIN

Body:
{
  "creatorId": "creator-1",
  "yearMonth": "2025-03"
}

Response 201:
{
  "id": "uuid",
  "creatorId": "creator-1",
  "yearMonth": "2025-03",
  "status": "PENDING",
  "totalSalesAmount": 260000,
  "totalRefundAmount": 110000,
  "netSalesAmount": 150000,
  "platformFee": 30000,
  "payoutAmount": 120000,
  "feeRatePercent": 20,
  "createdAt": "2026-05-27T10:00:00+09:00",
  "confirmedAt": null,
  "paidAt": null
}
```

### 정산 확정 (PENDING → CONFIRMED)

```
PATCH /api/settlements/{id}/confirm
Header: X-User-Role: ADMIN

Response 200: SettlementRecordResponse (status: CONFIRMED, confirmedAt 포함)
```

### 지급 완료 (CONFIRMED → PAID)

```
PATCH /api/settlements/{id}/pay
Header: X-User-Role: ADMIN

Response 200: SettlementRecordResponse (status: PAID, paidAt 포함)
```

### 크리에이터 정산 이력 조회

```
GET /api/settlements/creators/{creatorId}/history
Header: X-User-Id, X-User-Role (ADMIN 또는 본인 CREATOR)

Response 200: List<SettlementRecordResponse>
```

---

## 데이터 모델

### `SettlementRecord` 엔티티

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | String (UUID) | PK |
| creatorId | String | 크리에이터 ID |
| yearMonth | String | "2025-03" 형식 |
| status | SettlementStatus | PENDING / CONFIRMED / PAID |
| totalSalesAmount | Long | |
| totalRefundAmount | Long | |
| netSalesAmount | Long | |
| platformFee | Long | |
| payoutAmount | Long | |
| feeRatePercent | Long | 정산 시점 수수료율 (이슈 #5 연결) |
| createdAt | OffsetDateTime | |
| confirmedAt | OffsetDateTime | nullable |
| paidAt | OffsetDateTime | nullable |

**인덱스**
- `UNIQUE(creator_id, year_month)` — 이슈 #4 중복 방지 연결 지점
- `INDEX(creator_id)` — 이력 조회 성능

### `SettlementStatus` enum

```java
PENDING, CONFIRMED, PAID
```

---

## 상태 전이 규칙

```
PENDING → CONFIRMED (confirm API)
CONFIRMED → PAID    (pay API)
```

- 역방향 전이 없음
- 잘못된 상태에서 전이 시도 → 400 InvalidRequestException

---

## 에러 처리

| 상황 | 응답 |
|------|------|
| 존재하지 않는 creatorId | 404 ResourceNotFoundException |
| 잘못된 yearMonth 형식 | 400 (YearMonthConverter 처리) |
| PENDING이 아닌데 confirm | 400 InvalidRequestException |
| CONFIRMED가 아닌데 pay | 400 InvalidRequestException |
| 존재하지 않는 id로 전이 | 404 ResourceNotFoundException |
| ADMIN이 아닌데 생성/전이 | 403 ForbiddenException |

---

## 테스트 시나리오

| 시나리오 | 기대 결과 |
|----------|-----------|
| creator-1 / 2025-03 정산 생성 | payoutAmount: 120,000 |
| PENDING → CONFIRMED → PAID 순차 전이 | 각 상태·타임스탬프 반영 |
| PENDING에서 pay 직접 요청 | 400 |
| CONFIRMED에서 confirm 재요청 | 400 |
| 존재하지 않는 creatorId | 404 |
| CREATOR 권한으로 생성 시도 | 403 |

---

## 설계 결정

- **계산 시점**: 정산 생성(`POST`) 시 스냅샷. 이후 SaleRecord 변경이 생겨도 기존 정산 결과 불변
- **feeRatePercent 저장**: 이슈 #5(수수료율 이력)와 연결. 지금은 FeePolicy.FEE_RATE_PERCENT(20) 사용
- **UNIQUE 제약**: 이슈 #4와 공유. 지금은 제약만 추가, 409 처리는 #4에서 구현

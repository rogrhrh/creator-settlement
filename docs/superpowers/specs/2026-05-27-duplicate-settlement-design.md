# 중복 정산 방지 설계 명세

작성일: 2026-05-27  
관련 이슈: #4

---

## 배경

`SettlementRecord`에 `UNIQUE(creator_id, year_month)` 제약이 DB 레벨에 걸려 있지만, 중복 생성 시 DB 예외가 그대로 노출된다. 애플리케이션 레벨에서 중복을 감지해 409 Conflict를 반환하고, race condition 대비 DB 예외도 409로 처리한다.

---

## 아키텍처

### 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `exception/DuplicateResourceException.java` | 신규 — 409용 예외 |
| `repository/SettlementRecordRepository.java` | `existsByCreatorIdAndYearMonth()` 추가 |
| `service/SettlementConfirmService.java` | `create()` 내 중복 체크 추가 |
| `exception/GlobalExceptionHandler.java` | `DuplicateResourceException` → 409, `DataIntegrityViolationException` → 409 핸들러 추가 |
| `SettlementConfirmIntegrationTest.java` | 중복 생성 409 테스트 추가 |

---

## 처리 흐름

```
POST /api/settlements (creator-1, 2025-03)
  ↓
existsByCreatorIdAndYearMonth("creator-1", "2025-03")
  ↓ true → DuplicateResourceException → 409
  ↓ false → save()
              ↓ UNIQUE 위반(race condition) → DataIntegrityViolationException → 409
              ↓ 성공 → 201
```

---

## API 명세

### 중복 생성 시

```
POST /api/settlements
Header: X-User-Role: ADMIN
Body: { "creatorId": "creator-1", "yearMonth": "2025-03" }

Response 409:
{ "message": "creator-1의 2025-03 정산이 이미 존재합니다." }
```

---

## 에러 처리

| 상황 | 예외 | 응답 |
|------|------|------|
| 이미 존재하는 creatorId + yearMonth | DuplicateResourceException | 409 Conflict |
| 동시 요청으로 UNIQUE 위반 | DataIntegrityViolationException | 409 Conflict |

---

## 테스트 시나리오

| 시나리오 | 기대 결과 |
|----------|-----------|
| creator-1 / 2025-03 정산 생성 후 동일 요청 재시도 | 409 Conflict |
| 다른 yearMonth(2025-04)로 재시도 | 201 Created |

---

## 설계 결정

- **Application 체크 우선**: 명확한 에러 메시지 제공 (`DuplicateResourceException`)
- **DB 예외 안전망**: race condition 방어를 위해 `DataIntegrityViolationException`도 409로 처리
- **DataIntegrityViolationException 범위**: 현재 유일한 UNIQUE 제약이 `(creator_id, year_month)`이므로 해당 핸들러가 409를 반환해도 혼동 없음

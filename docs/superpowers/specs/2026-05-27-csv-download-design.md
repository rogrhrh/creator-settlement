# 정산 내역 CSV 다운로드 설계 명세

작성일: 2026-05-27  
관련 이슈: #9

---

## 배경

크리에이터 정산 이력을 JSON으로 조회하는 API는 이미 존재한다.  
동일 데이터를 CSV 파일로 다운로드할 수 있는 엔드포인트를 추가한다.

---

## 아키텍처

### 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `service/SettlementConfirmService.java` | `getHistoryCsv()` 메서드 추가 |
| `controller/SettlementController.java` | CSV 다운로드 엔드포인트 추가 |
| `integration/SettlementConfirmIntegrationTest.java` | CSV 다운로드 테스트 추가 |

---

## API 명세

### CSV 다운로드

```
GET /api/settlements/creators/{creatorId}/history/csv
Header: X-User-Id, X-User-Role (ADMIN 또는 본인 CREATOR)

Response 200:
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="settlement_{creatorId}.csv"
Body: CSV 파일 내용
```

---

## CSV 포맷

### 헤더 행
```
id,creatorId,yearMonth,status,totalSalesAmount,totalRefundAmount,netSalesAmount,platformFee,payoutAmount,feeRatePercent,createdAt,confirmedAt,paidAt
```

### 데이터 행 예시
```
550e8400-...,creator-1,2025-03,PAID,260000,110000,150000,30000,120000,20,2026-05-27T10:00:00+09:00,2026-05-27T11:00:00+09:00,2026-05-27T12:00:00+09:00
```

### 규칙
- 인코딩: UTF-8
- 구분자: 쉼표(,)
- null 값(confirmedAt, paidAt): 빈 문자열로 표현
- 정렬: yearMonth 내림차순 (기존 `getHistory()`와 동일)
- 정산 이력 없는 경우: 헤더 행만 포함한 빈 CSV 반환 (200)

---

## 에러 처리

| 상황 | 응답 |
|------|------|
| 권한 없음 (타 CREATOR 접근) | 403 ForbiddenException |
| 정산 이력 없음 | 200, 헤더만 있는 빈 CSV |

---

## 구현 상세

### `SettlementConfirmService.getHistoryCsv()`

```java
public String getHistoryCsv(String creatorId) {
    List<SettlementRecord> records =
        settlementRecordRepository.findByCreatorIdOrderByYearMonthDesc(creatorId);
    // StringJoiner로 CSV 생성, null → ""
}
```

### `SettlementController` 엔드포인트

```java
@GetMapping("/creators/{creatorId}/history/csv")
public ResponseEntity<byte[]> getSettlementHistoryCsv(
        @PathVariable String creatorId,
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader("X-User-Role") String userRole) {
    authValidator.validateCreatorAccess(userId, userRole, creatorId);
    byte[] csv = settlementConfirmService.getHistoryCsv(creatorId)
                     .getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
        .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"settlement_" + creatorId + ".csv\"")
        .body(csv);
}
```

---

## 테스트 시나리오

| 시나리오 | 기대 결과 |
|----------|-----------|
| ADMIN으로 creator-1 CSV 다운로드 | 200, Content-Type: text/csv, 헤더+데이터 행 포함 |
| 본인 CREATOR로 CSV 다운로드 | 200 |
| 타 CREATOR로 CSV 다운로드 | 403 |
| 정산 이력 없는 creatorId | 200, 헤더 행만 포함 |

---

## 설계 결정

- **서비스에서 CSV 생성**: 컨트롤러는 HTTP 응답 처리만 담당, 서비스에서 CSV 문자열 생성 → 책임 분리
- **외부 라이브러리 없음**: StringJoiner로 충분, 의존성 최소화
- **byte[] 반환**: UTF-8 인코딩 명시, 한글 파일명 인코딩 이슈 방지
- **빈 CSV 200 반환**: 이력 없음은 에러가 아니라 정상 상태

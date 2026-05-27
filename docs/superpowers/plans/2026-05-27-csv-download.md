# 정산 내역 CSV 다운로드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 크리에이터 정산 이력을 CSV 파일로 다운로드할 수 있는 API를 추가한다.

**Architecture:** `SettlementConfirmService.getHistoryCsv()`가 StringJoiner로 CSV 문자열을 생성하고, 컨트롤러가 `ResponseEntity<byte[]>`로 반환한다. 외부 라이브러리 없이 구현하며, 기존 `getHistory()` 쿼리를 재사용한다.

**Tech Stack:** Spring Boot 4, Java 21 (StringJoiner), MockMvc

---

## 파일 구조

### 수정
| 파일 | 변경 내용 |
|------|-----------|
| `src/main/java/com/ahn/settlement/service/SettlementConfirmService.java` | `getHistoryCsv()` 메서드 추가 |
| `src/main/java/com/ahn/settlement/controller/SettlementController.java` | CSV 다운로드 엔드포인트 추가 |
| `src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java` | CSV 다운로드 테스트 추가 |

---

## Task 1: SettlementConfirmService — getHistoryCsv() 추가

**Files:**
- Modify: `src/main/java/com/ahn/settlement/service/SettlementConfirmService.java`

- [ ] **Step 1: `getHistoryCsv()` 메서드 추가**

기존 `getHistory()` 메서드 아래에 추가한다.

import 추가:
```java
import com.ahn.settlement.entity.SettlementRecord;
import java.util.StringJoiner;
```

메서드:
```java
@Transactional(readOnly = true)
public String getHistoryCsv(String creatorId) {
    List<SettlementRecord> records =
            settlementRecordRepository.findByCreatorIdOrderByYearMonthDesc(creatorId);

    StringJoiner csv = new StringJoiner("\n");
    csv.add("id,creatorId,yearMonth,status,totalSalesAmount,totalRefundAmount," +
            "netSalesAmount,platformFee,payoutAmount,feeRatePercent,createdAt,confirmedAt,paidAt");

    for (SettlementRecord r : records) {
        csv.add(String.join(",",
                r.getId(),
                r.getCreatorId(),
                r.getYearMonth(),
                r.getStatus().name(),
                String.valueOf(r.getTotalSalesAmount()),
                String.valueOf(r.getTotalRefundAmount()),
                String.valueOf(r.getNetSalesAmount()),
                String.valueOf(r.getPlatformFee()),
                String.valueOf(r.getPayoutAmount()),
                String.valueOf(r.getFeeRatePercent()),
                r.getCreatedAt().toString(),
                r.getConfirmedAt() != null ? r.getConfirmedAt().toString() : "",
                r.getPaidAt() != null ? r.getPaidAt().toString() : ""
        ));
    }
    return csv.toString();
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/ahn/settlement/service/SettlementConfirmService.java
git commit -m "feat: SettlementConfirmService에 getHistoryCsv() 추가"
```

---

## Task 2: SettlementController 엔드포인트 추가 + 통합 테스트

**Files:**
- Modify: `src/main/java/com/ahn/settlement/controller/SettlementController.java`
- Modify: `src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java`

- [ ] **Step 1: 통합 테스트 추가 (실패 확인용)**

`SettlementConfirmIntegrationTest.java` 파일의 마지막 `}` 앞에 추가:

```java
@Test
void ADMIN으로_CSV_다운로드_성공() throws Exception {
    String body = objectMapper.writeValueAsString(
            Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));
    mockMvc.perform(post("/api/settlements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("X-User-Role", "ADMIN"))
            .andExpect(status().isCreated());

    mockMvc.perform(get("/api/settlements/creators/creator-1/history/csv")
                    .header("X-User-Id", "admin-1")
                    .header("X-User-Role", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id,creatorId,yearMonth")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("2025-03")));
}

@Test
void 본인_CREATOR로_CSV_다운로드_성공() throws Exception {
    mockMvc.perform(get("/api/settlements/creators/creator-1/history/csv")
                    .header("X-User-Id", "creator-1")
                    .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id,creatorId,yearMonth")));
}

@Test
void 타_CREATOR로_CSV_다운로드시_403() throws Exception {
    mockMvc.perform(get("/api/settlements/creators/creator-1/history/csv")
                    .header("X-User-Id", "creator-2")
                    .header("X-User-Role", "CREATOR"))
            .andExpect(status().isForbidden());
}

@Test
void 정산_이력_없는_경우_헤더만_포함된_CSV() throws Exception {
    mockMvc.perform(get("/api/settlements/creators/creator-3/history/csv")
                    .header("X-User-Id", "admin-1")
                    .header("X-User-Role", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(content().string("id,creatorId,yearMonth,status,totalSalesAmount," +
                    "totalRefundAmount,netSalesAmount,platformFee,payoutAmount," +
                    "feeRatePercent,createdAt,confirmedAt,paidAt"));
}
```

import 추가 (파일 상단):
```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.SettlementConfirmIntegrationTest.ADMIN으로_CSV_다운로드_성공"
```

Expected: FAILED (엔드포인트 미구현)

- [ ] **Step 3: `SettlementController`에 CSV 엔드포인트 추가**

import 추가:
```java
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
```

`getSettlementHistory()` 메서드 아래에 추가:

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

- [ ] **Step 4: 전체 테스트 통과 확인**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, 모든 테스트 통과

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/ahn/settlement/controller/SettlementController.java
git add src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java
git commit -m "feat: 정산 내역 CSV 다운로드 API 추가 및 통합 테스트 작성

closes #9"
```

---

## 최종 확인

- [ ] **전체 테스트 통과**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL

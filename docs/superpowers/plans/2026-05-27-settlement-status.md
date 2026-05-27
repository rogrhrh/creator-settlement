# 정산 확정 상태 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정산 결과를 DB에 영속화하고 PENDING → CONFIRMED → PAID 상태 전이를 관리하는 API를 구현한다.

**Architecture:** `SettlementRecord` 엔티티로 정산 결과를 저장하고, `SettlementConfirmService`가 생성·상태 전이를 담당한다. 계산 로직은 `SettlementService.calculateSettlement()`로 추출해 재사용한다. 기존 실시간 조회 API(`GET /api/settlements/creators/{creatorId}`)는 변경 없다.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Jakarta Validation, MockMvc

---

## 파일 구조

### 신규 생성
| 파일 | 역할 |
|------|------|
| `src/main/java/com/ahn/settlement/entity/SettlementStatus.java` | 상태 enum |
| `src/main/java/com/ahn/settlement/entity/SettlementRecord.java` | 정산 결과 엔티티 |
| `src/main/java/com/ahn/settlement/repository/SettlementRecordRepository.java` | 정산 레코드 조회 |
| `src/main/java/com/ahn/settlement/dto/request/SettlementCreateRequest.java` | 정산 생성 요청 DTO |
| `src/main/java/com/ahn/settlement/dto/response/SettlementRecordResponse.java` | 정산 레코드 응답 DTO |
| `src/main/java/com/ahn/settlement/service/SettlementConfirmService.java` | 생성·상태 전이 서비스 |
| `src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java` | 통합 테스트 |

### 수정
| 파일 | 변경 내용 |
|------|-----------|
| `src/main/java/com/ahn/settlement/service/SettlementService.java` | `calculateSettlement()` 메서드 추가 |
| `src/main/java/com/ahn/settlement/controller/SettlementController.java` | 새 엔드포인트 4개 추가 |

---

## Task 1: SettlementStatus + SettlementRecord 엔티티

**Files:**
- Create: `src/main/java/com/ahn/settlement/entity/SettlementStatus.java`
- Create: `src/main/java/com/ahn/settlement/entity/SettlementRecord.java`

- [ ] **Step 1: `SettlementStatus` enum 생성**

```java
package com.ahn.settlement.entity;

public enum SettlementStatus {
    PENDING, CONFIRMED, PAID
}
```

- [ ] **Step 2: `SettlementRecord` 엔티티 생성**

```java
package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "settlement_records",
        indexes = { @Index(name = "idx_settlement_records_creator_id", columnList = "creator_id") },
        uniqueConstraints = { @UniqueConstraint(name = "uq_settlement_creator_year_month",
                columnNames = {"creator_id", "year_month"}) }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementRecord {

    @Id
    private String id;

    @Column(name = "creator_id", nullable = false)
    private String creatorId;

    @Column(name = "year_month", nullable = false)
    private String yearMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(nullable = false)
    private Long totalSalesAmount;

    @Column(nullable = false)
    private Long totalRefundAmount;

    @Column(nullable = false)
    private Long netSalesAmount;

    @Column(nullable = false)
    private Long platformFee;

    @Column(nullable = false)
    private Long payoutAmount;

    @Column(nullable = false)
    private Long feeRatePercent;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime confirmedAt;

    @Column
    private OffsetDateTime paidAt;

    public SettlementRecord(String id, String creatorId, String yearMonth,
                            long totalSalesAmount, long totalRefundAmount,
                            long netSalesAmount, long platformFee, long payoutAmount,
                            long feeRatePercent) {
        this.id = id;
        this.creatorId = creatorId;
        this.yearMonth = yearMonth;
        this.status = SettlementStatus.PENDING;
        this.totalSalesAmount = totalSalesAmount;
        this.totalRefundAmount = totalRefundAmount;
        this.netSalesAmount = netSalesAmount;
        this.platformFee = platformFee;
        this.payoutAmount = payoutAmount;
        this.feeRatePercent = feeRatePercent;
        this.createdAt = OffsetDateTime.now();
    }

    public void confirm() {
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = OffsetDateTime.now();
    }

    public void pay() {
        this.status = SettlementStatus.PAID;
        this.paidAt = OffsetDateTime.now();
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
.\gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/entity/SettlementStatus.java
git add src/main/java/com/ahn/settlement/entity/SettlementRecord.java
git commit -m "feat: SettlementRecord 엔티티 및 SettlementStatus enum 추가"
```

---

## Task 2: Repository + DTOs

**Files:**
- Create: `src/main/java/com/ahn/settlement/repository/SettlementRecordRepository.java`
- Create: `src/main/java/com/ahn/settlement/dto/request/SettlementCreateRequest.java`
- Create: `src/main/java/com/ahn/settlement/dto/response/SettlementRecordResponse.java`

- [ ] **Step 1: `SettlementRecordRepository` 생성**

```java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, String> {
    List<SettlementRecord> findByCreatorIdOrderByYearMonthDesc(String creatorId);
}
```

- [ ] **Step 2: `SettlementCreateRequest` 생성**

```java
package com.ahn.settlement.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SettlementCreateRequest(
        @NotBlank String creatorId,
        @NotBlank String yearMonth
) {}
```

- [ ] **Step 3: `SettlementRecordResponse` 생성**

```java
package com.ahn.settlement.dto.response;

import com.ahn.settlement.entity.SettlementRecord;

import java.time.OffsetDateTime;

public record SettlementRecordResponse(
        String id,
        String creatorId,
        String yearMonth,
        String status,
        long totalSalesAmount,
        long totalRefundAmount,
        long netSalesAmount,
        long platformFee,
        long payoutAmount,
        long feeRatePercent,
        OffsetDateTime createdAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime paidAt
) {
    public static SettlementRecordResponse from(SettlementRecord record) {
        return new SettlementRecordResponse(
                record.getId(),
                record.getCreatorId(),
                record.getYearMonth(),
                record.getStatus().name(),
                record.getTotalSalesAmount(),
                record.getTotalRefundAmount(),
                record.getNetSalesAmount(),
                record.getPlatformFee(),
                record.getPayoutAmount(),
                record.getFeeRatePercent(),
                record.getCreatedAt(),
                record.getConfirmedAt(),
                record.getPaidAt()
        );
    }
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
.\gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/ahn/settlement/repository/SettlementRecordRepository.java
git add src/main/java/com/ahn/settlement/dto/request/SettlementCreateRequest.java
git add src/main/java/com/ahn/settlement/dto/response/SettlementRecordResponse.java
git commit -m "feat: SettlementRecordRepository 및 정산 생성/응답 DTO 추가"
```

---

## Task 3: SettlementService — calculateSettlement() 추출

**Files:**
- Modify: `src/main/java/com/ahn/settlement/service/SettlementService.java`

- [ ] **Step 1: `calculateSettlement()` 메서드 추가**

`SettlementService`에 아래 메서드를 추가한다. 기존 `getMonthlySettlement()`은 변경하지 않는다.

```java
public SettlementResult calculateSettlement(String creatorId, YearMonth yearMonth) {
    KstDateRange range = KstDateRange.of(yearMonth);
    SalesSummary sales = saleRecordRepository.summarizeByCreatorAndPeriod(
            creatorId, range.start(), range.end());
    RefundsSummary refunds = refundRecordRepository.summarizeByCreatorAndPeriod(
            creatorId, range.start(), range.end());
    return SettlementCalculator.calculate(
            sales.totalAmount(), refunds.totalRefund(), FeePolicy.FEE_RATE_PERCENT);
}
```

import 추가:
```java
import com.ahn.settlement.domain.SettlementResult;
```

- [ ] **Step 2: 기존 테스트 전체 통과 확인**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/ahn/settlement/service/SettlementService.java
git commit -m "feat: SettlementService에 calculateSettlement() 메서드 추출"
```

---

## Task 4: SettlementConfirmService 구현

**Files:**
- Create: `src/main/java/com/ahn/settlement/service/SettlementConfirmService.java`

- [ ] **Step 1: `SettlementConfirmService` 생성**

```java
package com.ahn.settlement.service;

import com.ahn.settlement.domain.FeePolicy;
import com.ahn.settlement.domain.SettlementResult;
import com.ahn.settlement.dto.request.SettlementCreateRequest;
import com.ahn.settlement.dto.response.SettlementRecordResponse;
import com.ahn.settlement.entity.SettlementRecord;
import com.ahn.settlement.entity.SettlementStatus;
import com.ahn.settlement.exception.InvalidRequestException;
import com.ahn.settlement.exception.ResourceNotFoundException;
import com.ahn.settlement.repository.CreatorRepository;
import com.ahn.settlement.repository.SettlementRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementConfirmService {

    private final SettlementRecordRepository settlementRecordRepository;
    private final CreatorRepository creatorRepository;
    private final SettlementService settlementService;

    @Transactional
    public SettlementRecordResponse create(SettlementCreateRequest request) {
        creatorRepository.findById(request.creatorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "크리에이터를 찾을 수 없습니다: " + request.creatorId()));

        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(request.yearMonth());
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("yearMonth 형식이 올바르지 않습니다. 예: 2025-03");
        }

        SettlementResult result = settlementService.calculateSettlement(request.creatorId(), yearMonth);

        SettlementRecord record = new SettlementRecord(
                UUID.randomUUID().toString(),
                request.creatorId(),
                request.yearMonth(),
                result.totalSalesAmount(),
                result.totalRefundAmount(),
                result.netSalesAmount(),
                result.platformFee(),
                result.payoutAmount(),
                FeePolicy.FEE_RATE_PERCENT
        );

        return SettlementRecordResponse.from(settlementRecordRepository.save(record));
    }

    @Transactional
    public SettlementRecordResponse confirm(String id) {
        SettlementRecord record = settlementRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("정산 내역을 찾을 수 없습니다: " + id));
        if (record.getStatus() != SettlementStatus.PENDING) {
            throw new InvalidRequestException("PENDING 상태의 정산만 확정할 수 있습니다.");
        }
        record.confirm();
        return SettlementRecordResponse.from(record);
    }

    @Transactional
    public SettlementRecordResponse pay(String id) {
        SettlementRecord record = settlementRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("정산 내역을 찾을 수 없습니다: " + id));
        if (record.getStatus() != SettlementStatus.CONFIRMED) {
            throw new InvalidRequestException("CONFIRMED 상태의 정산만 지급 완료 처리할 수 있습니다.");
        }
        record.pay();
        return SettlementRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<SettlementRecordResponse> getHistory(String creatorId) {
        return settlementRecordRepository.findByCreatorIdOrderByYearMonthDesc(creatorId)
                .stream()
                .map(SettlementRecordResponse::from)
                .toList();
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
.\gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/ahn/settlement/service/SettlementConfirmService.java
git commit -m "feat: SettlementConfirmService 구현 — 정산 생성, 확정, 지급 완료, 이력 조회"
```

---

## Task 5: SettlementController 엔드포인트 추가 + 통합 테스트

**Files:**
- Modify: `src/main/java/com/ahn/settlement/controller/SettlementController.java`
- Create: `src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java`

- [ ] **Step 1: 통합 테스트 작성 (실패 확인용)**

```java
package com.ahn.settlement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementConfirmIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void 정산_생성_creator1_2025_03_payoutAmount_120000() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payoutAmount").value(120000))
                .andExpect(jsonPath("$.feeRatePercent").value(20))
                .andExpect(jsonPath("$.creatorId").value("creator-1"))
                .andExpect(jsonPath("$.yearMonth").value("2025-03"));
    }

    @Test
    void PENDING_CONFIRMED_PAID_순차_상태_전이() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        String response = mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(patch("/api/settlements/" + id + "/confirm")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        mockMvc.perform(patch("/api/settlements/" + id + "/pay")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
    }

    @Test
    void PENDING_상태에서_pay_직접_요청시_400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        String response = mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(patch("/api/settlements/" + id + "/pay")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void CONFIRMED_상태에서_confirm_재요청시_400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        String response = mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(patch("/api/settlements/" + id + "/confirm")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/settlements/" + id + "/confirm")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_creatorId_정산_생성시_404() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "not-exist", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void CREATOR_권한으로_정산_생성시_403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", "creator-1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 크리에이터_정산_이력_조회() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/settlements/creators/creator-1/history")
                        .header("X-User-Id", "creator-1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].yearMonth").value("2025-03"));
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
.\gradlew test --tests "com.ahn.settlement.integration.SettlementConfirmIntegrationTest"
```

Expected: FAILED (엔드포인트 미구현)

- [ ] **Step 3: `SettlementController`에 엔드포인트 추가**

기존 필드에 `SettlementConfirmService` 추가, 4개 엔드포인트 추가:

```java
private final SettlementConfirmService settlementConfirmService;

@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public SettlementRecordResponse createSettlement(
        @RequestBody @Valid SettlementCreateRequest request,
        @RequestHeader("X-User-Role") String userRole) {
    authValidator.validateAdminAccess(userRole);
    return settlementConfirmService.create(request);
}

@PatchMapping("/{id}/confirm")
public SettlementRecordResponse confirmSettlement(
        @PathVariable String id,
        @RequestHeader("X-User-Role") String userRole) {
    authValidator.validateAdminAccess(userRole);
    return settlementConfirmService.confirm(id);
}

@PatchMapping("/{id}/pay")
public SettlementRecordResponse paySettlement(
        @PathVariable String id,
        @RequestHeader("X-User-Role") String userRole) {
    authValidator.validateAdminAccess(userRole);
    return settlementConfirmService.pay(id);
}

@GetMapping("/creators/{creatorId}/history")
public List<SettlementRecordResponse> getSettlementHistory(
        @PathVariable String creatorId,
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader("X-User-Role") String userRole) {
    authValidator.validateCreatorAccess(userId, userRole, creatorId);
    return settlementConfirmService.getHistory(creatorId);
}
```

import 추가:
```java
import com.ahn.settlement.dto.request.SettlementCreateRequest;
import com.ahn.settlement.dto.response.SettlementRecordResponse;
import com.ahn.settlement.service.SettlementConfirmService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.List;
```

- [ ] **Step 4: 전체 테스트 통과 확인**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL, 모든 테스트 통과

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/ahn/settlement/controller/SettlementController.java
git add src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java
git commit -m "feat: 정산 생성·확정·지급 완료·이력 조회 API 추가 및 통합 테스트 작성"
```

---

## 최종 확인

- [ ] **전체 테스트 통과**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL

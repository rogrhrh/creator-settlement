# 수수료율 변경 이력 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 수수료율을 DB 테이블로 관리하여, 정산 계산 시 판매 기준일에 유효한 수수료율을 자동 적용하고 ADMIN API로 이력 등록/조회 가능하게 한다.

**Architecture:** `FeePolicyHistory` 엔티티에 `(ratePercent, effectiveFrom)` 이력 저장. `FeePolicyService.getRateFor(LocalDate)` 가 `effectiveFrom <= targetDate` 조건으로 가장 최근 rate를 조회. `SettlementService`·`SettlementConfirmService`가 `FeePolicy` 상수 대신 `FeePolicyService`를 주입받아 사용.

**Tech Stack:** Spring Boot, Spring Data JPA, JUnit 5, MockMvc

---

## 파일 구조

| 역할 | 경로 |
|------|------|
| 신규 Entity | `src/main/java/com/ahn/settlement/entity/FeePolicyHistory.java` |
| 신규 Repository | `src/main/java/com/ahn/settlement/repository/FeePolicyHistoryRepository.java` |
| 신규 Service | `src/main/java/com/ahn/settlement/service/FeePolicyService.java` |
| 신규 Request DTO | `src/main/java/com/ahn/settlement/dto/request/FeePolicyCreateRequest.java` |
| 신규 Response DTO | `src/main/java/com/ahn/settlement/dto/response/FeePolicyResponse.java` |
| 신규 Controller | `src/main/java/com/ahn/settlement/controller/FeePolicyController.java` |
| 신규 통합 테스트 | `src/test/java/com/ahn/settlement/integration/FeePolicyIntegrationTest.java` |
| 수정 | `src/main/java/com/ahn/settlement/init/DataLoader.java` |
| 수정 | `src/main/java/com/ahn/settlement/service/SettlementService.java` |
| 수정 | `src/main/java/com/ahn/settlement/service/SettlementConfirmService.java` |
| 삭제 | `src/main/java/com/ahn/settlement/domain/FeePolicy.java` |

---

## Task 1: FeePolicyHistory 엔티티 + Repository

**Files:**
- Create: `src/main/java/com/ahn/settlement/entity/FeePolicyHistory.java`
- Create: `src/main/java/com/ahn/settlement/repository/FeePolicyHistoryRepository.java`

- [ ] **Step 1: FeePolicyHistory 엔티티 작성**

```java
// src/main/java/com/ahn/settlement/entity/FeePolicyHistory.java
package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
    name = "fee_policy_history",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_fee_policy_effective_from",
        columnNames = "effective_from"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeePolicyHistory {

    @Id
    private String id;

    @Column(nullable = false)
    private Long ratePercent;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    public FeePolicyHistory(String id, Long ratePercent, LocalDate effectiveFrom) {
        this.id = id;
        this.ratePercent = ratePercent;
        this.effectiveFrom = effectiveFrom;
    }
}
```

- [ ] **Step 2: FeePolicyHistoryRepository 작성**

```java
// src/main/java/com/ahn/settlement/repository/FeePolicyHistoryRepository.java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.FeePolicyHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeePolicyHistoryRepository extends JpaRepository<FeePolicyHistory, String> {

    Optional<FeePolicyHistory> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);

    List<FeePolicyHistory> findAllByOrderByEffectiveFromDesc();

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/entity/FeePolicyHistory.java \
        src/main/java/com/ahn/settlement/repository/FeePolicyHistoryRepository.java
git commit -m "feat: FeePolicyHistory 엔티티 및 Repository 추가"
```

---

## Task 2: FeePolicyService

**Files:**
- Create: `src/main/java/com/ahn/settlement/service/FeePolicyService.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/com/ahn/settlement/integration/FeePolicyIntegrationTest.java
package com.ahn.settlement.integration;

import com.ahn.settlement.entity.FeePolicyHistory;
import com.ahn.settlement.repository.FeePolicyHistoryRepository;
import com.ahn.settlement.service.FeePolicyService;
import com.ahn.settlement.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class FeePolicyIntegrationTest {

    @Autowired FeePolicyService feePolicyService;
    @Autowired FeePolicyHistoryRepository feePolicyHistoryRepository;

    @BeforeEach
    void setUp() {
        feePolicyHistoryRepository.deleteAll();
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-init", 20L, LocalDate.of(2020, 1, 1)));
    }

    @Test
    void getRateFor_시드_데이터_기준_20퍼센트_반환() {
        long rate = feePolicyService.getRateFor(LocalDate.of(2025, 3, 1));
        assertThat(rate).isEqualTo(20L);
    }

    @Test
    void getRateFor_변경_후_이전_날짜는_구_수수료율_반환() {
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-new", 10L, LocalDate.of(2025, 4, 1)));

        assertThat(feePolicyService.getRateFor(LocalDate.of(2025, 3, 31))).isEqualTo(20L);
        assertThat(feePolicyService.getRateFor(LocalDate.of(2025, 4, 1))).isEqualTo(10L);
        assertThat(feePolicyService.getRateFor(LocalDate.of(2025, 5, 1))).isEqualTo(10L);
    }

    @Test
    void add_중복_effectiveFrom_시_DuplicateResourceException() {
        assertThatThrownBy(() -> feePolicyService.add(15L, LocalDate.of(2020, 1, 1)))
            .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void getHistory_내림차순_반환() {
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-b", 15L, LocalDate.of(2025, 4, 1)));

        List<FeePolicyHistory> history = feePolicyService.getHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getEffectiveFrom()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(history.get(1).getEffectiveFrom()).isEqualTo(LocalDate.of(2020, 1, 1));
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.FeePolicyIntegrationTest" 2>&1 | tail -20
```

Expected: FAIL (FeePolicyService 없음)

- [ ] **Step 3: FeePolicyService 구현**

```java
// src/main/java/com/ahn/settlement/service/FeePolicyService.java
package com.ahn.settlement.service;

import com.ahn.settlement.entity.FeePolicyHistory;
import com.ahn.settlement.exception.DuplicateResourceException;
import com.ahn.settlement.repository.FeePolicyHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeePolicyService {

    private final FeePolicyHistoryRepository feePolicyHistoryRepository;

    public long getRateFor(LocalDate date) {
        return feePolicyHistoryRepository
            .findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
            .map(FeePolicyHistory::getRatePercent)
            .orElseThrow(() -> new IllegalStateException("적용 가능한 수수료율이 없습니다: " + date));
    }

    public List<FeePolicyHistory> getHistory() {
        return feePolicyHistoryRepository.findAllByOrderByEffectiveFromDesc();
    }

    @Transactional
    public FeePolicyHistory add(Long ratePercent, LocalDate effectiveFrom) {
        if (feePolicyHistoryRepository.existsByEffectiveFrom(effectiveFrom)) {
            throw new DuplicateResourceException("이미 등록된 수수료율 날짜입니다: " + effectiveFrom);
        }
        return feePolicyHistoryRepository.save(
            new FeePolicyHistory(UUID.randomUUID().toString(), ratePercent, effectiveFrom));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.FeePolicyIntegrationTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/ahn/settlement/service/FeePolicyService.java \
        src/test/java/com/ahn/settlement/integration/FeePolicyIntegrationTest.java
git commit -m "feat: FeePolicyService 구현 및 테스트 추가"
```

---

## Task 3: DataLoader에 초기 수수료율 시드 추가

**Files:**
- Modify: `src/main/java/com/ahn/settlement/init/DataLoader.java`

- [ ] **Step 1: DataLoader에 FeePolicyHistoryRepository 주입 및 시드 추가**

`DataLoader.java`의 필드와 `run()` 메서드를 아래와 같이 수정:

```java
// 필드 추가 (기존 필드 아래에 추가)
private final FeePolicyHistoryRepository feePolicyHistoryRepository;
```

`run()` 메서드 마지막 줄 `log.info(...)` 바로 위에 추가:

```java
feePolicyHistoryRepository.save(
    new FeePolicyHistory("fee-policy-1", 20L, LocalDate.of(2020, 1, 1)));
```

import 추가:
```java
import com.ahn.settlement.entity.FeePolicyHistory;
import com.ahn.settlement.repository.FeePolicyHistoryRepository;
import java.time.LocalDate;
```

- [ ] **Step 2: 기존 전체 테스트 통과 확인**

```bash
./gradlew cleanTest test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/ahn/settlement/init/DataLoader.java
git commit -m "feat: DataLoader에 초기 수수료율 시드 추가"
```

---

## Task 4: SettlementService — FeePolicy 상수 → FeePolicyService 교체

**Files:**
- Modify: `src/main/java/com/ahn/settlement/service/SettlementService.java`

- [ ] **Step 1: SettlementService 수정**

`SettlementService`에 `FeePolicyService` 필드 추가:

```java
private final FeePolicyService feePolicyService;
```

`getMonthlySettlement()` 메서드의 `SettlementCalculator.calculate(...)` 호출 부분을:

```java
// 변경 전
SettlementResult result = SettlementCalculator.calculate(
        sales.totalAmount(), refunds.totalRefund(), FeePolicy.FEE_RATE_PERCENT);
```

아래로 교체:

```java
// 변경 후
long feeRate = feePolicyService.getRateFor(yearMonth.atDay(1));
SettlementResult result = SettlementCalculator.calculate(
        sales.totalAmount(), refunds.totalRefund(), feeRate);
```

`calculateSettlement()` 메서드:

```java
// 변경 전
return SettlementCalculator.calculate(
        sales.totalAmount(), refunds.totalRefund(), FeePolicy.FEE_RATE_PERCENT);
```

```java
// 변경 후
long feeRate = feePolicyService.getRateFor(yearMonth.atDay(1));
return SettlementCalculator.calculate(
        sales.totalAmount(), refunds.totalRefund(), feeRate);
```

`getAdminSettlement()` 메서드에서 `buildSummary` 호출 전에 rate 조회 후 `buildSummary` 시그니처 변경:

```java
// getAdminSettlement() 내부 — summaries 빌드 전에 추가
long feeRate = feePolicyService.getRateFor(startDate);

List<AdminSettlementResponse.CreatorSettlementSummary> summaries = allCreatorIds.stream()
        .map(id -> buildSummary(id, salesMap, refundMap, feeRate))
        .toList();
```

`buildSummary()` 시그니처 변경:

```java
// 변경 전
private AdminSettlementResponse.CreatorSettlementSummary buildSummary(
        String creatorId,
        Map<String, SaleAggregate> salesMap,
        Map<String, RefundAggregate> refundMap)

// 변경 후
private AdminSettlementResponse.CreatorSettlementSummary buildSummary(
        String creatorId,
        Map<String, SaleAggregate> salesMap,
        Map<String, RefundAggregate> refundMap,
        long feeRatePercent)
```

`buildSummary()` 내부 `SettlementCalculator.calculate(...)` 호출:

```java
// 변경 전
SettlementResult result = SettlementCalculator.calculate(
        sale.totalAmount(), refund.totalRefund(), FeePolicy.FEE_RATE_PERCENT);
```

```java
// 변경 후
SettlementResult result = SettlementCalculator.calculate(
        sale.totalAmount(), refund.totalRefund(), feeRatePercent);
```

`FeePolicy` import 및 `import com.ahn.settlement.domain.FeePolicy;` 줄 삭제.

- [ ] **Step 2: 기존 전체 테스트 통과 확인**

```bash
./gradlew cleanTest test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/ahn/settlement/service/SettlementService.java
git commit -m "feat: SettlementService — FeePolicy 상수를 FeePolicyService 조회로 교체"
```

---

## Task 5: SettlementConfirmService 교체 + FeePolicy 삭제

**Files:**
- Modify: `src/main/java/com/ahn/settlement/service/SettlementConfirmService.java`
- Delete: `src/main/java/com/ahn/settlement/domain/FeePolicy.java`

- [ ] **Step 1: SettlementConfirmService 수정**

`FeePolicyService` 필드 추가:

```java
private final FeePolicyService feePolicyService;
```

`create()` 메서드에서 `SettlementRecord` 생성 직전에 rate 조회 추가:

```java
// 변경 전
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
```

```java
// 변경 후
long feeRate = feePolicyService.getRateFor(yearMonth.atDay(1));
SettlementRecord record = new SettlementRecord(
        UUID.randomUUID().toString(),
        request.creatorId(),
        request.yearMonth(),
        result.totalSalesAmount(),
        result.totalRefundAmount(),
        result.netSalesAmount(),
        result.platformFee(),
        result.payoutAmount(),
        feeRate
);
```

`import com.ahn.settlement.domain.FeePolicy;` 줄 삭제.

- [ ] **Step 2: FeePolicy.java 삭제**

```bash
rm src/main/java/com/ahn/settlement/domain/FeePolicy.java
```

- [ ] **Step 3: 전체 테스트 통과 확인**

```bash
./gradlew cleanTest test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add -A
git commit -m "feat: SettlementConfirmService FeePolicyService 교체 및 FeePolicy 클래스 삭제"
```

---

## Task 6: FeePolicyController + DTOs

**Files:**
- Create: `src/main/java/com/ahn/settlement/dto/request/FeePolicyCreateRequest.java`
- Create: `src/main/java/com/ahn/settlement/dto/response/FeePolicyResponse.java`
- Create: `src/main/java/com/ahn/settlement/controller/FeePolicyController.java`

- [ ] **Step 1: FeePolicyCreateRequest 작성**

```java
// src/main/java/com/ahn/settlement/dto/request/FeePolicyCreateRequest.java
package com.ahn.settlement.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FeePolicyCreateRequest(
    @NotNull @Min(0) Long ratePercent,
    @NotNull LocalDate effectiveFrom
) {}
```

- [ ] **Step 2: FeePolicyResponse 작성**

```java
// src/main/java/com/ahn/settlement/dto/response/FeePolicyResponse.java
package com.ahn.settlement.dto.response;

import com.ahn.settlement.entity.FeePolicyHistory;

import java.time.LocalDate;

public record FeePolicyResponse(String id, Long ratePercent, LocalDate effectiveFrom) {

    public static FeePolicyResponse from(FeePolicyHistory h) {
        return new FeePolicyResponse(h.getId(), h.getRatePercent(), h.getEffectiveFrom());
    }
}
```

- [ ] **Step 3: FeePolicyController 작성**

```java
// src/main/java/com/ahn/settlement/controller/FeePolicyController.java
package com.ahn.settlement.controller;

import com.ahn.settlement.auth.AuthValidator;
import com.ahn.settlement.dto.request.FeePolicyCreateRequest;
import com.ahn.settlement.dto.response.FeePolicyResponse;
import com.ahn.settlement.service.FeePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fee-policies")
@RequiredArgsConstructor
public class FeePolicyController {

    private final FeePolicyService feePolicyService;
    private final AuthValidator authValidator;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeePolicyResponse addFeePolicy(
            @RequestBody @Valid FeePolicyCreateRequest request,
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateAdminAccess(userRole);
        return FeePolicyResponse.from(
            feePolicyService.add(request.ratePercent(), request.effectiveFrom()));
    }

    @GetMapping
    public List<FeePolicyResponse> getFeeHistory(
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateAdminAccess(userRole);
        return feePolicyService.getHistory().stream()
            .map(FeePolicyResponse::from)
            .toList();
    }
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/ahn/settlement/dto/request/FeePolicyCreateRequest.java \
        src/main/java/com/ahn/settlement/dto/response/FeePolicyResponse.java \
        src/main/java/com/ahn/settlement/controller/FeePolicyController.java
git commit -m "feat: FeePolicyController 및 DTO 추가"
```

---

## Task 7: API 통합 테스트 및 수수료율 변경 효과 검증

**Files:**
- Modify: `src/test/java/com/ahn/settlement/integration/FeePolicyIntegrationTest.java`

- [ ] **Step 1: API 레벨 통합 테스트로 교체**

기존 `FeePolicyIntegrationTest.java`를 아래 내용으로 교체:

```java
// src/test/java/com/ahn/settlement/integration/FeePolicyIntegrationTest.java
package com.ahn.settlement.integration;

import com.ahn.settlement.entity.FeePolicyHistory;
import com.ahn.settlement.repository.FeePolicyHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeePolicyIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired FeePolicyHistoryRepository feePolicyHistoryRepository;

    @BeforeEach
    void setUp() {
        feePolicyHistoryRepository.deleteAll();
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-init", 20L, LocalDate.of(2020, 1, 1)));
    }

    @Test
    void POST_수수료율_등록_성공() throws Exception {
        mockMvc.perform(post("/api/fee-policies")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ratePercent\": 15, \"effectiveFrom\": \"2025-06-01\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ratePercent").value(15))
            .andExpect(jsonPath("$.effectiveFrom").value("2025-06-01"));
    }

    @Test
    void POST_중복_effectiveFrom_409_반환() throws Exception {
        mockMvc.perform(post("/api/fee-policies")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ratePercent\": 15, \"effectiveFrom\": \"2020-01-01\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void POST_ADMIN_아닌_역할_403_반환() throws Exception {
        mockMvc.perform(post("/api/fee-policies")
                .header("X-User-Role", "CREATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ratePercent\": 15, \"effectiveFrom\": \"2025-06-01\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void GET_이력_조회_내림차순() throws Exception {
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-new", 10L, LocalDate.of(2025, 4, 1)));

        mockMvc.perform(get("/api/fee-policies")
                .header("X-User-Role", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].effectiveFrom").value("2025-04-01"))
            .andExpect(jsonPath("$[1].effectiveFrom").value("2020-01-01"));
    }

    @Test
    void 수수료율_변경_후_이전_월_정산은_구_수수료율_적용() throws Exception {
        // 10% 수수료율 2025-04-01부터 적용
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-new", 10L, LocalDate.of(2025, 4, 1)));

        // 2025-03 creator-1 정산 — 20% 수수료율 적용
        // 총 판매 260000, 환불 110000, 순매출 150000, 수수료 30000(20%), 지급 120000
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                .param("yearMonth", "2025-03")
                .header("X-User-Id", "creator-1")
                .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platformFee").value(30000))
            .andExpect(jsonPath("$.payoutAmount").value(120000));
    }

    @Test
    void 수수료율_변경_후_이후_월_정산은_신_수수료율_적용() throws Exception {
        // 10% 수수료율 2025-03-01부터 적용 (2025-03 데이터에 적용되도록)
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-new", 10L, LocalDate.of(2025, 3, 1)));

        // 2025-03 creator-1 정산 — 10% 수수료율 적용
        // 순매출 150000, 수수료 15000(10%), 지급 135000
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                .param("yearMonth", "2025-03")
                .header("X-User-Id", "creator-1")
                .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platformFee").value(15000))
            .andExpect(jsonPath("$.payoutAmount").value(135000));
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew test --tests "com.ahn.settlement.integration.FeePolicyIntegrationTest" 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL, 6 tests passed

- [ ] **Step 3: 전체 테스트 통과 확인**

```bash
./gradlew cleanTest test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/ahn/settlement/integration/FeePolicyIntegrationTest.java
git commit -m "test: 수수료율 이력 관리 API 통합 테스트 추가"
```

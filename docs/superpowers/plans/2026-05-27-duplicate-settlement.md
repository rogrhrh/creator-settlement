# 중복 정산 방지 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 동일 creatorId + yearMonth로 정산 중복 생성 시 409 Conflict를 반환한다.

**Architecture:** `SettlementConfirmService.create()`에서 저장 전 `existsByCreatorIdAndYearMonth()` 조회로 중복을 감지해 `DuplicateResourceException`을 던진다. `GlobalExceptionHandler`에서 이를 409로 처리하고, race condition 대비로 `DataIntegrityViolationException`도 409로 처리한다.

**Tech Stack:** Spring Boot 4, Spring Data JPA, MockMvc

---

## 파일 구조

### 신규 생성
| 파일 | 역할 |
|------|------|
| `src/main/java/com/ahn/settlement/exception/DuplicateResourceException.java` | 409용 예외 |

### 수정
| 파일 | 변경 내용 |
|------|-----------|
| `src/main/java/com/ahn/settlement/repository/SettlementRecordRepository.java` | `existsByCreatorIdAndYearMonth()` 추가 |
| `src/main/java/com/ahn/settlement/service/SettlementConfirmService.java` | `create()` 내 중복 체크 추가 |
| `src/main/java/com/ahn/settlement/exception/GlobalExceptionHandler.java` | 409 핸들러 2개 추가 |
| `src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java` | 중복 409 테스트 추가 |

---

## Task 1: DuplicateResourceException + GlobalExceptionHandler 409 핸들러

**Files:**
- Create: `src/main/java/com/ahn/settlement/exception/DuplicateResourceException.java`
- Modify: `src/main/java/com/ahn/settlement/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: `DuplicateResourceException` 생성**

```java
package com.ahn.settlement.exception;

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: `GlobalExceptionHandler`에 409 핸들러 2개 추가**

기존 `handleForbidden()` 메서드 아래에 추가한다:

```java
@ExceptionHandler(DuplicateResourceException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleDuplicate(DuplicateResourceException e) {
    return new ErrorResponse(e.getMessage());
}

@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException e) {
    return new ErrorResponse("이미 존재하는 정산입니다.");
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/exception/DuplicateResourceException.java
git add src/main/java/com/ahn/settlement/exception/GlobalExceptionHandler.java
git commit -m "feat: DuplicateResourceException 추가 및 GlobalExceptionHandler에 409 핸들러 추가"
```

---

## Task 2: Repository 메서드 추가 + 서비스 중복 체크

**Files:**
- Modify: `src/main/java/com/ahn/settlement/repository/SettlementRecordRepository.java`
- Modify: `src/main/java/com/ahn/settlement/service/SettlementConfirmService.java`

- [ ] **Step 1: 통합 테스트에 중복 409 케이스 추가 (실패 확인용)**

`src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java`의 기존 테스트 목록 끝에 추가:

```java
@Test
void 동일_기간_정산_중복_생성시_409() throws Exception {
    String body = objectMapper.writeValueAsString(
            Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

    mockMvc.perform(post("/api/settlements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("X-User-Role", "ADMIN"))
            .andExpect(status().isCreated());

    mockMvc.perform(post("/api/settlements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("X-User-Role", "ADMIN"))
            .andExpect(status().isConflict());
}

@Test
void 다른_yearMonth는_중복_아님() throws Exception {
    String body1 = objectMapper.writeValueAsString(
            Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));
    String body2 = objectMapper.writeValueAsString(
            Map.of("creatorId", "creator-1", "yearMonth", "2025-04"));

    mockMvc.perform(post("/api/settlements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body1)
                    .header("X-User-Role", "ADMIN"))
            .andExpect(status().isCreated());

    mockMvc.perform(post("/api/settlements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body2)
                    .header("X-User-Role", "ADMIN"))
            .andExpect(status().isCreated());
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.SettlementConfirmIntegrationTest.동일_기간_정산_중복_생성시_409"
```

Expected: FAILED (현재 409 대신 500 또는 다른 응답)

- [ ] **Step 3: `SettlementRecordRepository`에 `existsByCreatorIdAndYearMonth()` 추가**

```java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, String> {
    List<SettlementRecord> findByCreatorIdOrderByYearMonthDesc(String creatorId);
    boolean existsByCreatorIdAndYearMonth(String creatorId, String yearMonth);
}
```

- [ ] **Step 4: `SettlementConfirmService.create()`에 중복 체크 추가**

`creatorRepository.findById()` 검사 다음 줄에 추가한다. import에 `DuplicateResourceException`도 추가한다.

```java
import com.ahn.settlement.exception.DuplicateResourceException;
```

`create()` 메서드 전체:

```java
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

    if (settlementRecordRepository.existsByCreatorIdAndYearMonth(request.creatorId(), request.yearMonth())) {
        throw new DuplicateResourceException(
                request.creatorId() + "의 " + request.yearMonth() + " 정산이 이미 존재합니다.");
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
```

- [ ] **Step 5: 전체 테스트 통과 확인**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, 모든 테스트 통과

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/ahn/settlement/repository/SettlementRecordRepository.java
git add src/main/java/com/ahn/settlement/service/SettlementConfirmService.java
git add src/test/java/com/ahn/settlement/integration/SettlementConfirmIntegrationTest.java
git commit -m "feat: 중복 정산 생성 방지 — 409 Conflict 반환

closes #4"
```

---

## 최종 확인

- [ ] **전체 테스트 통과**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL

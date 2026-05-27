# 리팩토링 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코드 품질 개선 — 타입 안전성, 성능(N+1·쿼리 수), 레이어 책임 분리를 확보하고 이후 선택 구현과의 연결 지점을 마련한다.

**Architecture:** 기존 API 스펙·엔티티 관계·테스트 시나리오는 변경하지 않는다. 리포지토리 반환 타입을 typed projection record로 교체하고, 서비스는 조립 로직만 담당하도록 슬림화한다. 컨트롤러 횡단 관심사(파싱·인증)는 전용 컴포넌트로 분리한다.

**Tech Stack:** Spring Boot 3, Spring Data JPA (Hibernate), Jakarta Persistence, Lombok

---

## 파일 구조

### 신규 생성
| 파일 | 역할 |
|------|------|
| `src/main/java/com/ahn/settlement/dto/query/SaleAggregate.java` | 운영자 집계용 판매 Projection |
| `src/main/java/com/ahn/settlement/dto/query/RefundAggregate.java` | 운영자 집계용 환불 Projection |
| `src/main/java/com/ahn/settlement/dto/query/SalesSummary.java` | 월별 정산용 판매 요약 Projection |
| `src/main/java/com/ahn/settlement/dto/query/RefundsSummary.java` | 월별 정산용 환불 요약 Projection |
| `src/main/java/com/ahn/settlement/converter/YearMonthConverter.java` | String → YearMonth 변환기 |
| `src/main/java/com/ahn/settlement/config/WebConfig.java` | 컨버터 등록 |
| `src/main/java/com/ahn/settlement/auth/AuthValidator.java` | 인증 검증 유틸 |

### 수정
| 파일 | 변경 내용 |
|------|-----------|
| `src/main/java/com/ahn/settlement/entity/SaleRecord.java` | `@Table` 인덱스 추가 |
| `src/main/java/com/ahn/settlement/entity/RefundRecord.java` | `@Table` 인덱스 추가 |
| `src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java` | JOIN FETCH, Projection 반환 타입, 쿼리 통합 |
| `src/main/java/com/ahn/settlement/repository/RefundRecordRepository.java` | Projection 반환 타입, 쿼리 통합 |
| `src/main/java/com/ahn/settlement/domain/SettlementCalculator.java` | feeRatePercent 파라미터 추가 |
| `src/main/java/com/ahn/settlement/service/SettlementService.java` | Projection 사용, 서비스 로직 간소화 |
| `src/main/java/com/ahn/settlement/controller/SettlementController.java` | AuthValidator 위임, YearMonth 파라미터 타입 변경 |
| `src/main/java/com/ahn/settlement/exception/GlobalExceptionHandler.java` | MethodArgumentTypeMismatchException 핸들러 추가 |
| `src/test/java/com/ahn/settlement/domain/SettlementCalculatorTest.java` | feeRatePercent 파라미터 반영 |

---

## Task 1: 엔티티 인덱스 추가 + JOIN FETCH

**Files:**
- Modify: `src/main/java/com/ahn/settlement/entity/SaleRecord.java`
- Modify: `src/main/java/com/ahn/settlement/entity/RefundRecord.java`
- Modify: `src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java`

- [ ] **Step 1: 기존 테스트 전체 통과 확인 (기준선 확보)**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL, 38 tests passed

- [ ] **Step 2: `SaleRecord`에 인덱스 추가**

`src/main/java/com/ahn/settlement/entity/SaleRecord.java`의 `@Table` 어노테이션을 아래로 교체:

```java
@Table(name = "sale_records", indexes = {
        @Index(name = "idx_sale_records_paid_at", columnList = "paid_at"),
        @Index(name = "idx_sale_records_course_id", columnList = "course_id")
})
```

import 추가:
```java
import jakarta.persistence.Index;
```

- [ ] **Step 3: `RefundRecord`에 인덱스 추가**

`src/main/java/com/ahn/settlement/entity/RefundRecord.java`의 `@Table` 어노테이션을 아래로 교체:

```java
@Table(name = "refund_records", indexes = {
        @Index(name = "idx_refund_records_canceled_at", columnList = "canceled_at"),
        @Index(name = "idx_refund_records_sale_record_id", columnList = "sale_record_id")
})
```

import 추가:
```java
import jakarta.persistence.Index;
```

- [ ] **Step 4: `findByCreatorId`, `findByCreatorIdAndPeriod`에 JOIN FETCH 추가**

`src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java`에서 두 쿼리를 아래로 교체:

```java
@Query("""
        SELECT s FROM SaleRecord s JOIN FETCH s.course
        WHERE s.course.creator.id = :creatorId
        ORDER BY s.paidAt
        """)
List<SaleRecord> findByCreatorId(@Param("creatorId") String creatorId);

@Query("""
        SELECT s FROM SaleRecord s JOIN FETCH s.course
        WHERE s.course.creator.id = :creatorId
          AND s.paidAt >= :start AND s.paidAt < :end
        ORDER BY s.paidAt
        """)
List<SaleRecord> findByCreatorIdAndPeriod(
        @Param("creatorId") String creatorId,
        @Param("start") OffsetDateTime start,
        @Param("end") OffsetDateTime end);
```

- [ ] **Step 5: 테스트 전체 통과 확인**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/ahn/settlement/entity/SaleRecord.java
git add src/main/java/com/ahn/settlement/entity/RefundRecord.java
git add src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java
git commit -m "refactor: 엔티티 인덱스 추가 및 N+1 해결을 위한 JOIN FETCH 적용"
```

---

## Task 2: Projection record 도입 — SaleAggregate, RefundAggregate

**Files:**
- Create: `src/main/java/com/ahn/settlement/dto/query/SaleAggregate.java`
- Create: `src/main/java/com/ahn/settlement/dto/query/RefundAggregate.java`
- Modify: `src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java`
- Modify: `src/main/java/com/ahn/settlement/repository/RefundRecordRepository.java`

- [ ] **Step 1: `SaleAggregate` record 생성**

```java
package com.ahn.settlement.dto.query;

public record SaleAggregate(
        String creatorId,
        String creatorName,
        long totalAmount,
        long saleCount
) {}
```

- [ ] **Step 2: `RefundAggregate` record 생성**

```java
package com.ahn.settlement.dto.query;

public record RefundAggregate(
        String creatorId,
        String creatorName,
        long totalRefund,
        long cancelCount
) {}
```

- [ ] **Step 3: `SaleRecordRepository.aggregateSalesByCreator` 반환 타입 교체**

`List<Object[]>` → `List<SaleAggregate>`, JPQL을 생성자 표현식으로 교체:

```java
@Query("""
        SELECT new com.ahn.settlement.dto.query.SaleAggregate(
            s.course.creator.id, s.course.creator.name,
            COALESCE(SUM(s.amount), 0), COUNT(s))
        FROM SaleRecord s
        WHERE s.paidAt >= :start AND s.paidAt < :end
        GROUP BY s.course.creator.id, s.course.creator.name
        """)
List<SaleAggregate> aggregateSalesByCreator(
        @Param("start") OffsetDateTime start,
        @Param("end") OffsetDateTime end);
```

import 추가:
```java
import com.ahn.settlement.dto.query.SaleAggregate;
```

- [ ] **Step 4: `RefundRecordRepository.aggregateRefundsByCreator` 반환 타입 교체**

```java
@Query("""
        SELECT new com.ahn.settlement.dto.query.RefundAggregate(
            r.saleRecord.course.creator.id, r.saleRecord.course.creator.name,
            COALESCE(SUM(r.refundAmount), 0), COUNT(r))
        FROM RefundRecord r
        WHERE r.canceledAt >= :start AND r.canceledAt < :end
        GROUP BY r.saleRecord.course.creator.id, r.saleRecord.course.creator.name
        """)
List<RefundAggregate> aggregateRefundsByCreator(
        @Param("start") OffsetDateTime start,
        @Param("end") OffsetDateTime end);
```

import 추가:
```java
import com.ahn.settlement.dto.query.RefundAggregate;
```

- [ ] **Step 5: 테스트 전체 통과 확인 (컴파일 에러 포함 — 서비스가 Object[] 캐스팅 중이라 컴파일 실패 예상)**

```bash
.\gradlew compileJava
```

Expected: 컴파일 에러 발생 (`SettlementService`의 `Object[]` 캐스팅 코드) — Task 3에서 수정 예정

- [ ] **Step 6: 커밋 (중간 상태, 빌드는 Task 3에서 복원)**

```bash
git add src/main/java/com/ahn/settlement/dto/query/SaleAggregate.java
git add src/main/java/com/ahn/settlement/dto/query/RefundAggregate.java
git add src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java
git add src/main/java/com/ahn/settlement/repository/RefundRecordRepository.java
git commit -m "refactor: Object[] 대신 SaleAggregate, RefundAggregate Projection record 도입"
```

---

## Task 3: 월별 정산 쿼리 통합 — SalesSummary, RefundsSummary

**Files:**
- Create: `src/main/java/com/ahn/settlement/dto/query/SalesSummary.java`
- Create: `src/main/java/com/ahn/settlement/dto/query/RefundsSummary.java`
- Modify: `src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java`
- Modify: `src/main/java/com/ahn/settlement/repository/RefundRecordRepository.java`

- [ ] **Step 1: `SalesSummary` record 생성**

```java
package com.ahn.settlement.dto.query;

public record SalesSummary(long totalAmount, long saleCount) {}
```

- [ ] **Step 2: `RefundsSummary` record 생성**

```java
package com.ahn.settlement.dto.query;

public record RefundsSummary(long totalRefund, long cancelCount) {}
```

- [ ] **Step 3: `SaleRecordRepository`에 통합 쿼리 추가 및 기존 4개 메서드 제거**

`sumAmountByCreatorAndPeriod`, `countByCreatorAndPeriod` 두 메서드를 제거하고 아래 추가:

```java
@Query("""
        SELECT new com.ahn.settlement.dto.query.SalesSummary(
            COALESCE(SUM(s.amount), 0), COUNT(s))
        FROM SaleRecord s
        WHERE s.course.creator.id = :creatorId
          AND s.paidAt >= :start AND s.paidAt < :end
        """)
SalesSummary summarizeByCreatorAndPeriod(
        @Param("creatorId") String creatorId,
        @Param("start") OffsetDateTime start,
        @Param("end") OffsetDateTime end);
```

import 추가:
```java
import com.ahn.settlement.dto.query.SalesSummary;
```

- [ ] **Step 4: `RefundRecordRepository`에 통합 쿼리 추가 및 기존 메서드 제거**

`sumRefundByCreatorAndPeriod`, `countByCreatorAndPeriod` 두 메서드를 제거하고 아래 추가:

```java
@Query("""
        SELECT new com.ahn.settlement.dto.query.RefundsSummary(
            COALESCE(SUM(r.refundAmount), 0), COUNT(r))
        FROM RefundRecord r
        WHERE r.saleRecord.course.creator.id = :creatorId
          AND r.canceledAt >= :start AND r.canceledAt < :end
        """)
RefundsSummary summarizeByCreatorAndPeriod(
        @Param("creatorId") String creatorId,
        @Param("start") OffsetDateTime start,
        @Param("end") OffsetDateTime end);
```

import 추가:
```java
import com.ahn.settlement.dto.query.RefundsSummary;
```

- [ ] **Step 5: 테스트 전체 통과 확인 (SettlementService 미수정으로 컴파일 에러 여전히 있음)**

Task 4에서 SettlementService를 함께 수정 후 최종 빌드 복원

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/ahn/settlement/dto/query/SalesSummary.java
git add src/main/java/com/ahn/settlement/dto/query/RefundsSummary.java
git add src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java
git add src/main/java/com/ahn/settlement/repository/RefundRecordRepository.java
git commit -m "refactor: 월별 정산 쿼리 4개를 SalesSummary, RefundsSummary 2개로 통합"
```

---

## Task 4: SettlementService 전면 리팩토링

**Files:**
- Modify: `src/main/java/com/ahn/settlement/service/SettlementService.java`

- [ ] **Step 1: `SettlementService` 전체 교체**

```java
package com.ahn.settlement.service;

import com.ahn.settlement.domain.FeePolicy;
import com.ahn.settlement.domain.SettlementCalculator;
import com.ahn.settlement.domain.SettlementResult;
import com.ahn.settlement.dto.query.RefundAggregate;
import com.ahn.settlement.dto.query.RefundsSummary;
import com.ahn.settlement.dto.query.SaleAggregate;
import com.ahn.settlement.dto.query.SalesSummary;
import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.repository.RefundRecordRepository;
import com.ahn.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SaleRecordRepository saleRecordRepository;
    private final RefundRecordRepository refundRecordRepository;

    public MonthlySettlementResponse getMonthlySettlement(String creatorId, YearMonth yearMonth) {
        var range = com.ahn.settlement.domain.KstDateRange.of(yearMonth);

        SalesSummary sales = saleRecordRepository.summarizeByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        RefundsSummary refunds = refundRecordRepository.summarizeByCreatorAndPeriod(
                creatorId, range.start(), range.end());

        SettlementResult result = SettlementCalculator.calculate(
                sales.totalAmount(), refunds.totalRefund());

        return new MonthlySettlementResponse(
                creatorId, yearMonth.toString(),
                result.totalSalesAmount(), result.totalRefundAmount(),
                result.netSalesAmount(), result.platformFee(), result.payoutAmount(),
                sales.saleCount(), refunds.cancelCount());
    }

    public AdminSettlementResponse getAdminSettlement(LocalDate startDate, LocalDate endDate) {
        var range = com.ahn.settlement.domain.KstDateRange.of(startDate, endDate);

        Map<String, SaleAggregate> salesMap = saleRecordRepository
                .aggregateSalesByCreator(range.start(), range.end())
                .stream()
                .collect(Collectors.toMap(SaleAggregate::creatorId, Function.identity()));

        Map<String, RefundAggregate> refundMap = refundRecordRepository
                .aggregateRefundsByCreator(range.start(), range.end())
                .stream()
                .collect(Collectors.toMap(RefundAggregate::creatorId, Function.identity()));

        Set<String> allCreatorIds = new LinkedHashSet<>();
        allCreatorIds.addAll(salesMap.keySet());
        allCreatorIds.addAll(refundMap.keySet());

        List<AdminSettlementResponse.CreatorSettlementSummary> summaries = allCreatorIds.stream()
                .map(id -> buildSummary(id, salesMap, refundMap))
                .toList();

        long totalPayout = summaries.stream()
                .mapToLong(AdminSettlementResponse.CreatorSettlementSummary::payoutAmount)
                .sum();

        return new AdminSettlementResponse(startDate.toString(), endDate.toString(), summaries, totalPayout);
    }

    private AdminSettlementResponse.CreatorSettlementSummary buildSummary(
            String creatorId,
            Map<String, SaleAggregate> salesMap,
            Map<String, RefundAggregate> refundMap) {

        SaleAggregate sale = salesMap.getOrDefault(creatorId,
                new SaleAggregate(creatorId, "", 0L, 0L));
        RefundAggregate refund = refundMap.getOrDefault(creatorId,
                new RefundAggregate(creatorId, "", 0L, 0L));
        String creatorName = sale.creatorName().isBlank() ? refund.creatorName() : sale.creatorName();

        SettlementResult result = SettlementCalculator.calculate(
                sale.totalAmount(), refund.totalRefund());

        return new AdminSettlementResponse.CreatorSettlementSummary(
                creatorId, creatorName,
                result.totalSalesAmount(), result.totalRefundAmount(),
                result.netSalesAmount(), result.platformFee(), result.payoutAmount());
    }
}
```

> `CreatorRepository` 의존성이 제거됨에 주의. import 정리 후 컴파일 확인.

- [ ] **Step 2: 테스트 전체 통과 확인 (Task 2~4 변경 누적 후 첫 전체 빌드)**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL, 모든 기존 테스트 통과

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/ahn/settlement/service/SettlementService.java
git commit -m "refactor: SettlementService Projection 기반으로 간소화 및 CreatorRepository 의존성 제거"
```

---

## Task 5: YearMonth 커스텀 컨버터 분리

**Files:**
- Create: `src/main/java/com/ahn/settlement/converter/YearMonthConverter.java`
- Create: `src/main/java/com/ahn/settlement/config/WebConfig.java`
- Modify: `src/main/java/com/ahn/settlement/exception/GlobalExceptionHandler.java`
- Modify: `src/main/java/com/ahn/settlement/controller/SettlementController.java`

- [ ] **Step 1: `YearMonthConverter` 생성**

```java
package com.ahn.settlement.converter;

import org.springframework.core.convert.converter.Converter;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public class YearMonthConverter implements Converter<String, YearMonth> {

    @Override
    public YearMonth convert(String source) {
        try {
            return YearMonth.parse(source);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("yearMonth 형식이 올바르지 않습니다. 예: 2025-03");
        }
    }
}
```

- [ ] **Step 2: `WebConfig` 생성 — 컨버터 등록**

```java
package com.ahn.settlement.config;

import com.ahn.settlement.converter.YearMonthConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new YearMonthConverter());
    }
}
```

- [ ] **Step 3: `GlobalExceptionHandler`에 `MethodArgumentTypeMismatchException` 핸들러 추가**

기존 `handleInvalidRequest` 메서드 아래에 추가:

```java
@ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ErrorResponse handleTypeMismatch(
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
    Throwable cause = e.getCause();
    String message = (cause != null) ? cause.getMessage() : "요청 파라미터 형식이 올바르지 않습니다.";
    return new ErrorResponse(message);
}
```

- [ ] **Step 4: `SettlementController` — `yearMonth` 파라미터 타입 교체 및 try-catch 제거**

`getMonthlySettlement` 메서드를 아래로 교체:

```java
@GetMapping("/creators/{creatorId}")
public MonthlySettlementResponse getMonthlySettlement(
        @PathVariable String creatorId,
        @RequestParam YearMonth yearMonth,
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader("X-User-Role") String userRole) {

    if (!"CREATOR".equals(userRole) && !"ADMIN".equals(userRole)) {
        throw new ForbiddenException("접근 권한이 없습니다.");
    }
    if ("CREATOR".equals(userRole) && !userId.equals(creatorId)) {
        throw new ForbiddenException("본인의 정산 내역만 조회할 수 있습니다.");
    }
    return settlementService.getMonthlySettlement(creatorId, yearMonth);
}
```

import 추가 (`DateTimeParseException` import 제거, `YearMonth` import 확인):
```java
import java.time.YearMonth;
```

- [ ] **Step 5: 테스트 전체 통과 확인**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/ahn/settlement/converter/YearMonthConverter.java
git add src/main/java/com/ahn/settlement/config/WebConfig.java
git add src/main/java/com/ahn/settlement/exception/GlobalExceptionHandler.java
git add src/main/java/com/ahn/settlement/controller/SettlementController.java
git commit -m "refactor: YearMonth 파싱을 커스텀 컨버터로 분리하고 GlobalExceptionHandler에 타입 오류 처리 추가"
```

---

## Task 6: AuthValidator 추출

**Files:**
- Create: `src/main/java/com/ahn/settlement/auth/AuthValidator.java`
- Modify: `src/main/java/com/ahn/settlement/controller/SettlementController.java`

- [ ] **Step 1: `AuthValidator` 생성**

```java
package com.ahn.settlement.auth;

import com.ahn.settlement.exception.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class AuthValidator {

    public void validateCreatorAccess(String userId, String userRole, String creatorId) {
        if (!"CREATOR".equals(userRole) && !"ADMIN".equals(userRole)) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }
        if ("CREATOR".equals(userRole) && !userId.equals(creatorId)) {
            throw new ForbiddenException("본인의 정산 내역만 조회할 수 있습니다.");
        }
    }

    public void validateAdminAccess(String userRole) {
        if (!"ADMIN".equals(userRole)) {
            throw new ForbiddenException("운영자만 접근할 수 있습니다.");
        }
    }
}
```

- [ ] **Step 2: `SettlementController`에 `AuthValidator` 주입 및 인증 로직 위임**

```java
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final AuthValidator authValidator;

    @GetMapping("/creators/{creatorId}")
    public MonthlySettlementResponse getMonthlySettlement(
            @PathVariable String creatorId,
            @RequestParam YearMonth yearMonth,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        authValidator.validateCreatorAccess(userId, userRole, creatorId);
        return settlementService.getMonthlySettlement(creatorId, yearMonth);
    }

    @GetMapping("/admin")
    public AdminSettlementResponse getAdminSettlement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-User-Role") String userRole) {

        authValidator.validateAdminAccess(userRole);
        if (startDate.isAfter(endDate)) {
            throw new InvalidRequestException("시작일이 종료일보다 늦을 수 없습니다.");
        }
        return settlementService.getAdminSettlement(startDate, endDate);
    }
}
```

- [ ] **Step 3: 테스트 전체 통과 확인**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/auth/AuthValidator.java
git add src/main/java/com/ahn/settlement/controller/SettlementController.java
git commit -m "refactor: 인증 검증 로직을 AuthValidator로 추출하여 컨트롤러 책임 분리"
```

---

## Task 7: SettlementCalculator feeRate 파라미터화

**Files:**
- Modify: `src/main/java/com/ahn/settlement/domain/SettlementCalculator.java`
- Modify: `src/test/java/com/ahn/settlement/domain/SettlementCalculatorTest.java`

- [ ] **Step 1: `SettlementCalculatorTest` 업데이트 (테스트 먼저)**

기존 테스트의 `calculate(totalSales, totalRefund)` 호출을 `calculate(totalSales, totalRefund, 20L)`로 수정:

파일 전체에서 `SettlementCalculator.calculate(` 패턴을 찾아 feeRatePercent 인수 추가.

예시 (실제 파일의 각 테스트 케이스에 적용):
```java
SettlementResult result = SettlementCalculator.calculate(totalSales, totalRefund, 20L);
```

- [ ] **Step 2: 테스트 실행 — 컴파일 에러 확인**

```bash
.\gradlew test --tests "com.ahn.settlement.domain.SettlementCalculatorTest"
```

Expected: 컴파일 에러 (`calculate` 시그니처 불일치) — Step 3에서 수정

- [ ] **Step 3: `SettlementCalculator` 시그니처 변경**

```java
package com.ahn.settlement.domain;

public class SettlementCalculator {

    private SettlementCalculator() {}

    public static SettlementResult calculate(long totalSalesAmount, long totalRefundAmount, long feeRatePercent) {
        long netSalesAmount = totalSalesAmount - totalRefundAmount;
        long platformFee = netSalesAmount * feeRatePercent / 100;
        long payoutAmount = netSalesAmount - platformFee;
        return new SettlementResult(totalSalesAmount, totalRefundAmount, netSalesAmount, platformFee, payoutAmount);
    }
}
```

- [ ] **Step 4: `SettlementService`의 `calculate` 호출에 `FeePolicy.FEE_RATE_PERCENT` 추가**

`SettlementService` 내 `calculate` 호출 2곳을 아래로 수정:

`getMonthlySettlement` 내:
```java
SettlementResult result = SettlementCalculator.calculate(
        sales.totalAmount(), refunds.totalRefund(), FeePolicy.FEE_RATE_PERCENT);
```

`buildSummary` 내:
```java
SettlementResult result = SettlementCalculator.calculate(
        sale.totalAmount(), refund.totalRefund(), FeePolicy.FEE_RATE_PERCENT);
```

import 추가:
```java
import com.ahn.settlement.domain.FeePolicy;
```

- [ ] **Step 5: 테스트 전체 통과 확인**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/ahn/settlement/domain/SettlementCalculator.java
git add src/main/java/com/ahn/settlement/service/SettlementService.java
git add src/test/java/com/ahn/settlement/domain/SettlementCalculatorTest.java
git commit -m "refactor: SettlementCalculator에 feeRatePercent 파라미터 추가로 수수료율 외부 주입 준비"
```

---

## 최종 확인

- [ ] **전체 테스트 통과**

```bash
.\gradlew test
```

Expected: BUILD SUCCESSFUL, 모든 테스트 통과

- [ ] **브랜치 PR 준비 — 변경 요약 확인**

```bash
git log --oneline
```

Expected: 7개 커밋 확인 (Task 1~7 각 1개)

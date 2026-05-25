# Creator Settlement API — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 크리에이터 정산 API를 Spring Boot 4.0.6 + Java 21 + H2 + JPA로 구현한다. 정산 계산 정확성, KST 월 경계 처리, 환불 누적 검증, 필수 시나리오 테스트 통과를 최우선으로 한다.

**Architecture:** 조회 API는 SaleRecord/RefundRecord를 실시간 집계하며 SettlementRecord를 사용하지 않는다. KST 기준 [start, end) 구간을 Java에서 계산해 JPQL range query로 전달한다. 수수료율은 FeePolicy 상수 클래스(20%)로 분리하고, 계산 로직은 SettlementCalculator에 집중한다.

**Tech Stack:** Spring Boot 4.0.6, Java 21, Spring Data JPA, H2, Lombok, Jakarta Validation, JUnit 5, MockMvc

---

## 파일 맵

```
src/main/java/com/ahn/settlement/
├── entity/
│   ├── Creator.java
│   ├── Course.java
│   ├── SaleRecord.java
│   └── RefundRecord.java
├── repository/
│   ├── CreatorRepository.java
│   ├── CourseRepository.java
│   ├── SaleRecordRepository.java
│   └── RefundRecordRepository.java
├── domain/
│   ├── FeePolicy.java
│   ├── KstDateRange.java
│   ├── SettlementResult.java
│   └── SettlementCalculator.java
├── dto/
│   ├── request/
│   │   ├── SaleRecordRequest.java
│   │   └── RefundRecordRequest.java
│   └── response/
│       ├── SaleRecordResponse.java
│       ├── MonthlySettlementResponse.java
│       ├── AdminSettlementResponse.java
│       └── ErrorResponse.java
├── service/
│   ├── SaleService.java
│   ├── RefundService.java
│   └── SettlementService.java
├── controller/
│   ├── SaleController.java
│   ├── RefundController.java
│   └── SettlementController.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   ├── InvalidRequestException.java
│   └── ForbiddenException.java
└── init/
    └── DataLoader.java

src/main/resources/
└── application.yaml

src/test/java/com/ahn/settlement/
├── domain/
│   ├── KstDateRangeTest.java
│   └── SettlementCalculatorTest.java
├── repository/
│   ├── SaleRecordRepositoryTest.java
│   └── RefundRecordRepositoryTest.java
├── service/
│   └── RefundServiceTest.java
└── integration/
    ├── SaleIntegrationTest.java
    ├── RefundIntegrationTest.java
    ├── SettlementIntegrationTest.java    ← 필수 시나리오 전부
    └── AdminSettlementIntegrationTest.java
```

---

## Task 1: application.yaml 설정

**Files:**
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: application.yaml 작성**

```yaml
spring:
  application:
    name: creator-settlement
  datasource:
    url: jdbc:h2:mem:settlementdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  h2:
    console:
      enabled: true
      path: /h2-console

server:
  port: 8080
```

- [ ] **Step 2: 애플리케이션 기동 확인**

```bash
./gradlew bootRun
```

Expected: `Started CreatorSettlementApplication` 로그 출력, 포트 8080 바인딩 성공

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/application.yaml
git commit -m "chore: configure H2 datasource and JPA settings"
```

---

## Task 2: KstDateRange — KST 구간 계산 유틸

**Files:**
- Create: `src/main/java/com/ahn/settlement/domain/KstDateRange.java`
- Create: `src/test/java/com/ahn/settlement/domain/KstDateRangeTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/ahn/settlement/domain/KstDateRangeTest.java`
```java
package com.ahn.settlement.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class KstDateRangeTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Test
    void yearMonth_start는_해당월_1일_0시_KST() {
        KstDateRange range = KstDateRange.of(YearMonth.of(2025, 3));
        assertThat(range.start()).isEqualTo(OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void yearMonth_end는_다음달_1일_0시_KST() {
        KstDateRange range = KstDateRange.of(YearMonth.of(2025, 3));
        assertThat(range.end()).isEqualTo(OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void yearMonth_12월_end는_다음해_1월_1일() {
        KstDateRange range = KstDateRange.of(YearMonth.of(2025, 12));
        assertThat(range.end()).isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void localDate_start는_당일_0시_KST() {
        KstDateRange range = KstDateRange.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
        assertThat(range.start()).isEqualTo(OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void localDate_end는_종료일_다음날_0시_KST() {
        KstDateRange range = KstDateRange.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
        assertThat(range.end()).isEqualTo(OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void 월_경계값_1월말_판매는_1월에_귀속() {
        KstDateRange jan = KstDateRange.of(YearMonth.of(2025, 1));
        OffsetDateTime sale5PaidAt = OffsetDateTime.of(2025, 1, 31, 23, 30, 0, 0, KST);
        assertThat(sale5PaidAt.isBefore(jan.end())).isTrue();
        assertThat(!sale5PaidAt.isBefore(jan.start())).isTrue();
    }

    @Test
    void 월_경계값_2월_취소는_2월에_귀속() {
        KstDateRange feb = KstDateRange.of(YearMonth.of(2025, 2));
        OffsetDateTime cancel3At = OffsetDateTime.of(2025, 2, 1, 0, 30, 0, 0, KST);
        assertThat(cancel3At.isBefore(feb.end())).isTrue();
        assertThat(!cancel3At.isBefore(feb.start())).isTrue();
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.domain.KstDateRangeTest"
```

Expected: FAILED — `KstDateRange` 클래스 없음

- [ ] **Step 3: KstDateRange 구현**

`src/main/java/com/ahn/settlement/domain/KstDateRange.java`
```java
package com.ahn.settlement.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

public record KstDateRange(OffsetDateTime start, OffsetDateTime end) {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    public static KstDateRange of(YearMonth yearMonth) {
        OffsetDateTime start = yearMonth.atDay(1).atStartOfDay().atOffset(KST);
        OffsetDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(KST);
        return new KstDateRange(start, end);
    }

    public static KstDateRange of(LocalDate startDate, LocalDate endDate) {
        OffsetDateTime start = startDate.atStartOfDay().atOffset(KST);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(KST);
        return new KstDateRange(start, end);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.domain.KstDateRangeTest"
```

Expected: 7 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/ahn/settlement/domain/KstDateRange.java \
        src/test/java/com/ahn/settlement/domain/KstDateRangeTest.java
git commit -m "feat: add KstDateRange utility for KST boundary calculation"
```

---

## Task 3: FeePolicy + SettlementResult + SettlementCalculator

**Files:**
- Create: `src/main/java/com/ahn/settlement/domain/FeePolicy.java`
- Create: `src/main/java/com/ahn/settlement/domain/SettlementResult.java`
- Create: `src/main/java/com/ahn/settlement/domain/SettlementCalculator.java`
- Create: `src/test/java/com/ahn/settlement/domain/SettlementCalculatorTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/ahn/settlement/domain/SettlementCalculatorTest.java`
```java
package com.ahn.settlement.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementCalculatorTest {

    @Test
    void 필수시나리오_creator1_2025_03_정산() {
        // sale-1(50000) + sale-2(50000) + sale-3(80000) + sale-4(80000) = 260000
        // cancel-1(80000) + cancel-2(30000) = 110000
        SettlementResult result = SettlementCalculator.calculate(260_000L, 110_000L);

        assertThat(result.totalSalesAmount()).isEqualTo(260_000L);
        assertThat(result.totalRefundAmount()).isEqualTo(110_000L);
        assertThat(result.netSalesAmount()).isEqualTo(150_000L);
        assertThat(result.platformFee()).isEqualTo(30_000L);
        assertThat(result.payoutAmount()).isEqualTo(120_000L);
    }

    @Test
    void 판매없고_환불없으면_전부_0() {
        SettlementResult result = SettlementCalculator.calculate(0L, 0L);

        assertThat(result.netSalesAmount()).isZero();
        assertThat(result.platformFee()).isZero();
        assertThat(result.payoutAmount()).isZero();
    }

    @Test
    void 수수료율_20퍼센트_정확히_적용() {
        SettlementResult result = SettlementCalculator.calculate(100_000L, 0L);

        assertThat(result.platformFee()).isEqualTo(20_000L);
        assertThat(result.payoutAmount()).isEqualTo(80_000L);
    }

    @Test
    void 부분환불_순매출에_잔여금액_남음() {
        // sale-4(80000) 부분 환불 cancel-2(30000) → 순매출 50000
        SettlementResult result = SettlementCalculator.calculate(80_000L, 30_000L);

        assertThat(result.netSalesAmount()).isEqualTo(50_000L);
        assertThat(result.platformFee()).isEqualTo(10_000L);
        assertThat(result.payoutAmount()).isEqualTo(40_000L);
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.domain.SettlementCalculatorTest"
```

Expected: FAILED — 클래스 없음

- [ ] **Step 3: FeePolicy 구현**

`src/main/java/com/ahn/settlement/domain/FeePolicy.java`
```java
package com.ahn.settlement.domain;

public class FeePolicy {

    public static final long FEE_RATE_PERCENT = 20;

    private FeePolicy() {}

    public static long calculateFee(long netSalesAmount) {
        return netSalesAmount * FEE_RATE_PERCENT / 100;
    }
}
```

- [ ] **Step 4: SettlementResult 구현**

`src/main/java/com/ahn/settlement/domain/SettlementResult.java`
```java
package com.ahn.settlement.domain;

public record SettlementResult(
        long totalSalesAmount,
        long totalRefundAmount,
        long netSalesAmount,
        long platformFee,
        long payoutAmount
) {}
```

- [ ] **Step 5: SettlementCalculator 구현**

`src/main/java/com/ahn/settlement/domain/SettlementCalculator.java`
```java
package com.ahn.settlement.domain;

public class SettlementCalculator {

    private SettlementCalculator() {}

    public static SettlementResult calculate(long totalSalesAmount, long totalRefundAmount) {
        long netSalesAmount = totalSalesAmount - totalRefundAmount;
        long platformFee = FeePolicy.calculateFee(netSalesAmount);
        long payoutAmount = netSalesAmount - platformFee;
        return new SettlementResult(totalSalesAmount, totalRefundAmount, netSalesAmount, platformFee, payoutAmount);
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.domain.SettlementCalculatorTest"
```

Expected: 4 tests PASSED

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/ahn/settlement/domain/ \
        src/test/java/com/ahn/settlement/domain/SettlementCalculatorTest.java
git commit -m "feat: add FeePolicy, SettlementResult, SettlementCalculator domain classes"
```

---

## Task 4: 엔티티 (Creator, Course, SaleRecord, RefundRecord)

**Files:**
- Create: `src/main/java/com/ahn/settlement/entity/Creator.java`
- Create: `src/main/java/com/ahn/settlement/entity/Course.java`
- Create: `src/main/java/com/ahn/settlement/entity/SaleRecord.java`
- Create: `src/main/java/com/ahn/settlement/entity/RefundRecord.java`

- [ ] **Step 1: Creator 엔티티 작성**

`src/main/java/com/ahn/settlement/entity/Creator.java`
```java
package com.ahn.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "creators")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Creator {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    public Creator(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
```

- [ ] **Step 2: Course 엔티티 작성**

`src/main/java/com/ahn/settlement/entity/Course.java`
```java
package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    @Column(nullable = false)
    private String title;

    public Course(String id, Creator creator, String title) {
        this.id = id;
        this.creator = creator;
        this.title = title;
    }
}
```

- [ ] **Step 3: SaleRecord 엔티티 작성**

`src/main/java/com/ahn/settlement/entity/SaleRecord.java`
```java
package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sale_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleRecord {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private OffsetDateTime paidAt;

    public SaleRecord(String id, Course course, String studentId, Long amount, OffsetDateTime paidAt) {
        this.id = id;
        this.course = course;
        this.studentId = studentId;
        this.amount = amount;
        this.paidAt = paidAt;
    }
}
```

- [ ] **Step 4: RefundRecord 엔티티 작성**

`src/main/java/com/ahn/settlement/entity/RefundRecord.java`
```java
package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "refund_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRecord {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_record_id", nullable = false)
    private SaleRecord saleRecord;

    @Column(nullable = false)
    private Long refundAmount;

    @Column(nullable = false)
    private OffsetDateTime canceledAt;

    public RefundRecord(String id, SaleRecord saleRecord, Long refundAmount, OffsetDateTime canceledAt) {
        this.id = id;
        this.saleRecord = saleRecord;
        this.refundAmount = refundAmount;
        this.canceledAt = canceledAt;
    }
}
```

- [ ] **Step 5: 애플리케이션 기동 및 DDL 확인**

```bash
./gradlew bootRun
```

Expected: 기동 성공, H2 콘솔 `http://localhost:8080/h2-console` 접속 후 `CREATORS`, `COURSES`, `SALE_RECORDS`, `REFUND_RECORDS` 테이블 확인

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/ahn/settlement/entity/
git commit -m "feat: add Creator, Course, SaleRecord, RefundRecord entities"
```

---

## Task 5: Repository — JPQL 집계 쿼리

**Files:**
- Create: `src/main/java/com/ahn/settlement/repository/CreatorRepository.java`
- Create: `src/main/java/com/ahn/settlement/repository/CourseRepository.java`
- Create: `src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java`
- Create: `src/main/java/com/ahn/settlement/repository/RefundRecordRepository.java`
- Create: `src/test/java/com/ahn/settlement/repository/SaleRecordRepositoryTest.java`
- Create: `src/test/java/com/ahn/settlement/repository/RefundRecordRepositoryTest.java`

- [ ] **Step 1: CreatorRepository, CourseRepository 작성**

`src/main/java/com/ahn/settlement/repository/CreatorRepository.java`
```java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.Creator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorRepository extends JpaRepository<Creator, String> {}
```

`src/main/java/com/ahn/settlement/repository/CourseRepository.java`
```java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, String> {}
```

- [ ] **Step 2: SaleRecordRepository 실패 테스트 작성**

`src/test/java/com/ahn/settlement/repository/SaleRecordRepositoryTest.java`
```java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.Course;
import com.ahn.settlement.entity.Creator;
import com.ahn.settlement.entity.SaleRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SaleRecordRepositoryTest {

    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CreatorRepository creatorRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private Creator creator;
    private Course course;

    @BeforeEach
    void setUp() {
        creator = creatorRepository.save(new Creator("creator-1", "김강사"));
        course = courseRepository.save(new Course("course-1", creator, "Spring Boot 입문"));
    }

    @Test
    void 기간_내_판매금액_합산() {
        OffsetDateTime start = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);

        saleRecordRepository.save(new SaleRecord("s1", course, "stu1", 50_000L,
                OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST)));
        saleRecordRepository.save(new SaleRecord("s2", course, "stu2", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));

        Long sum = saleRecordRepository.sumAmountByCreatorAndPeriod("creator-1", start, end);

        assertThat(sum).isEqualTo(130_000L);
    }

    @Test
    void 기간_외_판매는_합산_제외() {
        OffsetDateTime start = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);

        saleRecordRepository.save(new SaleRecord("s3", course, "stu3", 60_000L,
                OffsetDateTime.of(2025, 2, 28, 23, 59, 59, 0, KST)));

        Long sum = saleRecordRepository.sumAmountByCreatorAndPeriod("creator-1", start, end);

        assertThat(sum).isZero();
    }

    @Test
    void 판매없는_기간_조회시_0반환() {
        OffsetDateTime start = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);

        Long sum = saleRecordRepository.sumAmountByCreatorAndPeriod("creator-1", start, end);

        assertThat(sum).isZero();
    }
}
```

- [ ] **Step 3: SaleRecordRepository 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.repository.SaleRecordRepositoryTest"
```

Expected: FAILED — 메서드 없음

- [ ] **Step 4: SaleRecordRepository 구현**

`src/main/java/com/ahn/settlement/repository/SaleRecordRepository.java`
```java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.SaleRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface SaleRecordRepository extends JpaRepository<SaleRecord, String> {

    @Query("""
            SELECT COALESCE(SUM(s.amount), 0)
            FROM SaleRecord s
            WHERE s.course.creator.id = :creatorId
              AND s.paidAt >= :start AND s.paidAt < :end
            """)
    Long sumAmountByCreatorAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT COUNT(s)
            FROM SaleRecord s
            WHERE s.course.creator.id = :creatorId
              AND s.paidAt >= :start AND s.paidAt < :end
            """)
    long countByCreatorAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT s FROM SaleRecord s
            WHERE s.course.creator.id = :creatorId
            ORDER BY s.paidAt
            """)
    List<SaleRecord> findByCreatorId(@Param("creatorId") String creatorId);

    @Query("""
            SELECT s FROM SaleRecord s
            WHERE s.course.creator.id = :creatorId
              AND s.paidAt >= :start AND s.paidAt < :end
            ORDER BY s.paidAt
            """)
    List<SaleRecord> findByCreatorIdAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT s.course.creator.id, s.course.creator.name,
                   COALESCE(SUM(s.amount), 0), COUNT(s)
            FROM SaleRecord s
            WHERE s.paidAt >= :start AND s.paidAt < :end
            GROUP BY s.course.creator.id, s.course.creator.name
            """)
    List<Object[]> aggregateSalesByCreator(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
```

- [ ] **Step 5: RefundRecordRepository 실패 테스트 작성**

`src/test/java/com/ahn/settlement/repository/RefundRecordRepositoryTest.java`
```java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RefundRecordRepositoryTest {

    @Autowired RefundRecordRepository refundRecordRepository;
    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CreatorRepository creatorRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private SaleRecord sale;

    @BeforeEach
    void setUp() {
        Creator creator = creatorRepository.save(new Creator("c1", "테스터"));
        Course course = courseRepository.save(new Course("co1", creator, "테스트 강의"));
        sale = saleRecordRepository.save(new SaleRecord("s1", course, "stu1", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));
    }

    @Test
    void 판매건별_환불합계_조회() {
        refundRecordRepository.save(new RefundRecord("r1", sale, 30_000L,
                OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST)));

        Long sum = refundRecordRepository.sumRefundBySaleRecordId("s1");

        assertThat(sum).isEqualTo(30_000L);
    }

    @Test
    void 환불없는_판매건_합계는_0() {
        Long sum = refundRecordRepository.sumRefundBySaleRecordId("s1");
        assertThat(sum).isZero();
    }

    @Test
    void 기간_내_크리에이터_환불합계() {
        OffsetDateTime start = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);

        refundRecordRepository.save(new RefundRecord("r1", sale, 30_000L,
                OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST)));

        Long sum = refundRecordRepository.sumRefundByCreatorAndPeriod("c1", start, end);

        assertThat(sum).isEqualTo(30_000L);
    }

    @Test
    void 월경계_취소는_취소월에만_반영() {
        // canceledAt이 2월 → 2월 쿼리에만 포함
        refundRecordRepository.save(new RefundRecord("r1", sale, 60_000L,
                OffsetDateTime.of(2025, 2, 1, 0, 30, 0, 0, KST)));

        OffsetDateTime marStart = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime marEnd = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);
        OffsetDateTime febStart = OffsetDateTime.of(2025, 2, 1, 0, 0, 0, 0, KST);
        OffsetDateTime febEnd = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);

        assertThat(refundRecordRepository.sumRefundByCreatorAndPeriod("c1", marStart, marEnd)).isZero();
        assertThat(refundRecordRepository.sumRefundByCreatorAndPeriod("c1", febStart, febEnd)).isEqualTo(60_000L);
    }
}
```

- [ ] **Step 6: RefundRecordRepository 구현**

`src/main/java/com/ahn/settlement/repository/RefundRecordRepository.java`
```java
package com.ahn.settlement.repository;

import com.ahn.settlement.entity.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, String> {

    @Query("""
            SELECT COALESCE(SUM(r.refundAmount), 0)
            FROM RefundRecord r
            WHERE r.saleRecord.id = :saleRecordId
            """)
    Long sumRefundBySaleRecordId(@Param("saleRecordId") String saleRecordId);

    @Query("""
            SELECT COALESCE(SUM(r.refundAmount), 0)
            FROM RefundRecord r
            WHERE r.saleRecord.course.creator.id = :creatorId
              AND r.canceledAt >= :start AND r.canceledAt < :end
            """)
    Long sumRefundByCreatorAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT COUNT(r)
            FROM RefundRecord r
            WHERE r.saleRecord.course.creator.id = :creatorId
              AND r.canceledAt >= :start AND r.canceledAt < :end
            """)
    long countByCreatorAndPeriod(
            @Param("creatorId") String creatorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            SELECT r.saleRecord.course.creator.id,
                   COALESCE(SUM(r.refundAmount), 0), COUNT(r)
            FROM RefundRecord r
            WHERE r.canceledAt >= :start AND r.canceledAt < :end
            GROUP BY r.saleRecord.course.creator.id
            """)
    List<Object[]> aggregateRefundsByCreator(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
```

- [ ] **Step 7: 리포지토리 테스트 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.repository.*"
```

Expected: 전체 PASSED

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/ahn/settlement/repository/ \
        src/test/java/com/ahn/settlement/repository/
git commit -m "feat: add repositories with JPQL aggregate queries"
```

---

## Task 6: 예외 클래스 + GlobalExceptionHandler

**Files:**
- Create: `src/main/java/com/ahn/settlement/exception/ResourceNotFoundException.java`
- Create: `src/main/java/com/ahn/settlement/exception/InvalidRequestException.java`
- Create: `src/main/java/com/ahn/settlement/exception/ForbiddenException.java`
- Create: `src/main/java/com/ahn/settlement/dto/response/ErrorResponse.java`
- Create: `src/main/java/com/ahn/settlement/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 예외 클래스 3개 작성**

`src/main/java/com/ahn/settlement/exception/ResourceNotFoundException.java`
```java
package com.ahn.settlement.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

`src/main/java/com/ahn/settlement/exception/InvalidRequestException.java`
```java
package com.ahn.settlement.exception;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
```

`src/main/java/com/ahn/settlement/exception/ForbiddenException.java`
```java
package com.ahn.settlement.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: ErrorResponse DTO 작성**

`src/main/java/com/ahn/settlement/dto/response/ErrorResponse.java`
```java
package com.ahn.settlement.dto.response;

public record ErrorResponse(String message) {}
```

- [ ] **Step 3: GlobalExceptionHandler 작성**

`src/main/java/com/ahn/settlement/exception/GlobalExceptionHandler.java`
```java
package com.ahn.settlement.exception;

import com.ahn.settlement.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidRequest(InvalidRequestException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ErrorResponse(message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception e) {
        return new ErrorResponse("서버 오류가 발생했습니다.");
    }
}
```

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/exception/ \
        src/main/java/com/ahn/settlement/dto/response/ErrorResponse.java
git commit -m "feat: add exception classes and GlobalExceptionHandler"
```

---

## Task 7: DataLoader — 샘플 데이터 삽입

**Files:**
- Create: `src/main/java/com/ahn/settlement/init/DataLoader.java`

- [ ] **Step 1: DataLoader 작성**

`src/main/java/com/ahn/settlement/init/DataLoader.java`
```java
package com.ahn.settlement.init;

import com.ahn.settlement.entity.*;
import com.ahn.settlement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements ApplicationRunner {

    private final CreatorRepository creatorRepository;
    private final CourseRepository courseRepository;
    private final SaleRecordRepository saleRecordRepository;
    private final RefundRecordRepository refundRecordRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Creator c1 = creatorRepository.save(new Creator("creator-1", "김강사"));
        Creator c2 = creatorRepository.save(new Creator("creator-2", "이강사"));
        Creator c3 = creatorRepository.save(new Creator("creator-3", "박강사"));

        Course co1 = courseRepository.save(new Course("course-1", c1, "Spring Boot 입문"));
        Course co2 = courseRepository.save(new Course("course-2", c1, "JPA 실전"));
        Course co3 = courseRepository.save(new Course("course-3", c2, "Kotlin 기초"));
        Course co4 = courseRepository.save(new Course("course-4", c3, "MSA 설계"));

        SaleRecord s1 = saleRecordRepository.save(new SaleRecord("sale-1", co1, "student-1", 50_000L,
                OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST)));
        SaleRecord s2 = saleRecordRepository.save(new SaleRecord("sale-2", co1, "student-2", 50_000L,
                OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, KST)));
        SaleRecord s3 = saleRecordRepository.save(new SaleRecord("sale-3", co2, "student-3", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));
        SaleRecord s4 = saleRecordRepository.save(new SaleRecord("sale-4", co2, "student-4", 80_000L,
                OffsetDateTime.of(2025, 3, 22, 11, 0, 0, 0, KST)));
        SaleRecord s5 = saleRecordRepository.save(new SaleRecord("sale-5", co3, "student-5", 60_000L,
                OffsetDateTime.of(2025, 1, 31, 23, 30, 0, 0, KST)));
        saleRecordRepository.save(new SaleRecord("sale-6", co3, "student-6", 60_000L,
                OffsetDateTime.of(2025, 3, 10, 16, 0, 0, 0, KST)));
        saleRecordRepository.save(new SaleRecord("sale-7", co4, "student-7", 120_000L,
                OffsetDateTime.of(2025, 2, 14, 10, 0, 0, 0, KST)));

        refundRecordRepository.save(new RefundRecord("cancel-1", s3, 80_000L,
                OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST)));
        refundRecordRepository.save(new RefundRecord("cancel-2", s4, 30_000L,
                OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST)));
        refundRecordRepository.save(new RefundRecord("cancel-3", s5, 60_000L,
                OffsetDateTime.of(2025, 2, 1, 0, 30, 0, 0, KST)));

        log.info("샘플 데이터 로드 완료");
    }
}
```

- [ ] **Step 2: 기동 및 H2 콘솔 데이터 확인**

```bash
./gradlew bootRun
```

브라우저에서 `http://localhost:8080/h2-console` 접속.
- JDBC URL: `jdbc:h2:mem:settlementdb`
- `SELECT * FROM SALE_RECORDS;` → 7건 확인
- `SELECT * FROM REFUND_RECORDS;` → 3건 확인

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/ahn/settlement/init/DataLoader.java
git commit -m "feat: add DataLoader with sample data from assignment"
```

---

## Task 8: 판매 등록 API (POST /api/sales)

**Files:**
- Create: `src/main/java/com/ahn/settlement/dto/request/SaleRecordRequest.java`
- Create: `src/main/java/com/ahn/settlement/dto/response/SaleRecordResponse.java`
- Create: `src/main/java/com/ahn/settlement/service/SaleService.java`
- Create: `src/main/java/com/ahn/settlement/controller/SaleController.java`
- Create: `src/test/java/com/ahn/settlement/integration/SaleIntegrationTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/ahn/settlement/integration/SaleIntegrationTest.java`
```java
package com.ahn.settlement.integration;

import com.ahn.settlement.entity.Course;
import com.ahn.settlement.entity.Creator;
import com.ahn.settlement.repository.CourseRepository;
import com.ahn.settlement.repository.CreatorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaleIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreatorRepository creatorRepository;
    @Autowired CourseRepository courseRepository;

    @BeforeEach
    void setUp() {
        Creator creator = creatorRepository.save(new Creator("creator-1", "김강사"));
        courseRepository.save(new Course("course-1", creator, "Spring Boot 입문"));
    }

    @Test
    void 판매_등록_성공() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "sale-test-1",
                "courseId", "course-1",
                "studentId", "student-1",
                "amount", 50000,
                "paidAt", "2025-03-05T10:00:00+09:00"
        );

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("sale-test-1"))
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    void 존재하지_않는_courseId_등록시_404() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "sale-test-2",
                "courseId", "course-9999",
                "studentId", "student-1",
                "amount", 50000,
                "paidAt", "2025-03-05T10:00:00+09:00"
        );

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void amount가_0이하이면_400() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "sale-test-3",
                "courseId", "course-1",
                "studentId", "student-1",
                "amount", 0,
                "paidAt", "2025-03-05T10:00:00+09:00"
        );

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.SaleIntegrationTest"
```

Expected: FAILED — 엔드포인트 없음

- [ ] **Step 3: Request/Response DTO 작성**

`src/main/java/com/ahn/settlement/dto/request/SaleRecordRequest.java`
```java
package com.ahn.settlement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record SaleRecordRequest(
        @NotBlank String id,
        @NotBlank String courseId,
        @NotBlank String studentId,
        @NotNull @Positive Long amount,
        @NotNull OffsetDateTime paidAt
) {}
```

`src/main/java/com/ahn/settlement/dto/response/SaleRecordResponse.java`
```java
package com.ahn.settlement.dto.response;

import com.ahn.settlement.entity.SaleRecord;

import java.time.OffsetDateTime;

public record SaleRecordResponse(
        String id,
        String courseId,
        String courseTitle,
        String studentId,
        Long amount,
        OffsetDateTime paidAt
) {
    public static SaleRecordResponse from(SaleRecord s) {
        return new SaleRecordResponse(
                s.getId(),
                s.getCourse().getId(),
                s.getCourse().getTitle(),
                s.getStudentId(),
                s.getAmount(),
                s.getPaidAt()
        );
    }
}
```

- [ ] **Step 4: SaleService 작성**

`src/main/java/com/ahn/settlement/service/SaleService.java`
```java
package com.ahn.settlement.service;

import com.ahn.settlement.domain.KstDateRange;
import com.ahn.settlement.dto.request.SaleRecordRequest;
import com.ahn.settlement.dto.response.SaleRecordResponse;
import com.ahn.settlement.entity.Course;
import com.ahn.settlement.entity.SaleRecord;
import com.ahn.settlement.exception.ResourceNotFoundException;
import com.ahn.settlement.repository.CourseRepository;
import com.ahn.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRecordRepository saleRecordRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public SaleRecordResponse register(SaleRecordRequest request) {
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("강의를 찾을 수 없습니다: " + request.courseId()));
        SaleRecord saleRecord = new SaleRecord(
                request.id(), course, request.studentId(), request.amount(), request.paidAt());
        saleRecordRepository.save(saleRecord);
        return SaleRecordResponse.from(saleRecord);
    }

    @Transactional(readOnly = true)
    public List<SaleRecordResponse> findByCreator(String creatorId, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            KstDateRange range = KstDateRange.of(startDate, endDate);
            return saleRecordRepository.findByCreatorIdAndPeriod(creatorId, range.start(), range.end())
                    .stream().map(SaleRecordResponse::from).toList();
        }
        return saleRecordRepository.findByCreatorId(creatorId)
                .stream().map(SaleRecordResponse::from).toList();
    }
}
```

- [ ] **Step 5: SaleController 작성**

`src/main/java/com/ahn/settlement/controller/SaleController.java`
```java
package com.ahn.settlement.controller;

import com.ahn.settlement.dto.request.SaleRecordRequest;
import com.ahn.settlement.dto.response.SaleRecordResponse;
import com.ahn.settlement.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleRecordResponse register(@RequestBody @Valid SaleRecordRequest request) {
        return saleService.register(request);
    }

    @GetMapping
    public List<SaleRecordResponse> findByCreator(
            @RequestParam String creatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return saleService.findByCreator(creatorId, startDate, endDate);
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.SaleIntegrationTest"
```

Expected: 3 tests PASSED

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/ahn/settlement/dto/request/SaleRecordRequest.java \
        src/main/java/com/ahn/settlement/dto/response/SaleRecordResponse.java \
        src/main/java/com/ahn/settlement/service/SaleService.java \
        src/main/java/com/ahn/settlement/controller/SaleController.java \
        src/test/java/com/ahn/settlement/integration/SaleIntegrationTest.java
git commit -m "feat: add sale record registration and query API"
```

---

## Task 9: 환불 등록 API (POST /api/refunds)

**Files:**
- Create: `src/main/java/com/ahn/settlement/dto/request/RefundRecordRequest.java`
- Create: `src/main/java/com/ahn/settlement/service/RefundService.java`
- Create: `src/main/java/com/ahn/settlement/controller/RefundController.java`
- Create: `src/test/java/com/ahn/settlement/integration/RefundIntegrationTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/ahn/settlement/integration/RefundIntegrationTest.java`
```java
package com.ahn.settlement.integration;

import com.ahn.settlement.entity.*;
import com.ahn.settlement.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefundIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreatorRepository creatorRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired RefundRecordRepository refundRecordRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @BeforeEach
    void setUp() {
        Creator c = creatorRepository.save(new Creator("creator-1", "김강사"));
        Course co = courseRepository.save(new Course("course-1", c, "Spring Boot 입문"));
        saleRecordRepository.save(new SaleRecord("sale-1", co, "stu-1", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));
    }

    @Test
    void 환불_등록_성공() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "cancel-t1",
                "saleRecordId", "sale-1",
                "refundAmount", 30000,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void 존재하지_않는_saleRecordId_환불시_404() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "cancel-t2",
                "saleRecordId", "sale-9999",
                "refundAmount", 30000,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 환불금액이_원결제금액_초과시_400() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "cancel-t3",
                "saleRecordId", "sale-1",
                "refundAmount", 90000,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 누적환불이_원결제금액_초과시_400() throws Exception {
        refundRecordRepository.save(new RefundRecord("cancel-existing", 
                saleRecordRepository.findById("sale-1").get(), 60_000L,
                OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST)));

        Map<String, Object> body = Map.of(
                "id", "cancel-t4",
                "saleRecordId", "sale-1",
                "refundAmount", 30000,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 환불금액_0이하이면_400() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "cancel-t5",
                "saleRecordId", "sale-1",
                "refundAmount", 0,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.RefundIntegrationTest"
```

Expected: FAILED

- [ ] **Step 3: RefundRecordRequest 작성**

`src/main/java/com/ahn/settlement/dto/request/RefundRecordRequest.java`
```java
package com.ahn.settlement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record RefundRecordRequest(
        @NotBlank String id,
        @NotBlank String saleRecordId,
        @NotNull @Positive Long refundAmount,
        @NotNull OffsetDateTime canceledAt
) {}
```

- [ ] **Step 4: RefundService 작성**

`src/main/java/com/ahn/settlement/service/RefundService.java`
```java
package com.ahn.settlement.service;

import com.ahn.settlement.dto.request.RefundRecordRequest;
import com.ahn.settlement.entity.RefundRecord;
import com.ahn.settlement.entity.SaleRecord;
import com.ahn.settlement.exception.InvalidRequestException;
import com.ahn.settlement.exception.ResourceNotFoundException;
import com.ahn.settlement.repository.RefundRecordRepository;
import com.ahn.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRecordRepository refundRecordRepository;
    private final SaleRecordRepository saleRecordRepository;

    @Transactional
    public void register(RefundRecordRequest request) {
        SaleRecord saleRecord = saleRecordRepository.findById(request.saleRecordId())
                .orElseThrow(() -> new ResourceNotFoundException("판매 내역을 찾을 수 없습니다: " + request.saleRecordId()));

        if (request.refundAmount() > saleRecord.getAmount()) {
            throw new InvalidRequestException("환불 금액이 원결제 금액을 초과할 수 없습니다.");
        }

        long alreadyRefunded = refundRecordRepository.sumRefundBySaleRecordId(request.saleRecordId());
        if (alreadyRefunded + request.refundAmount() > saleRecord.getAmount()) {
            throw new InvalidRequestException("누적 환불 금액이 원결제 금액을 초과할 수 없습니다.");
        }

        refundRecordRepository.save(new RefundRecord(
                request.id(), saleRecord, request.refundAmount(), request.canceledAt()));
    }
}
```

- [ ] **Step 5: RefundController 작성**

`src/main/java/com/ahn/settlement/controller/RefundController.java`
```java
package com.ahn.settlement.controller;

import com.ahn.settlement.dto.request.RefundRecordRequest;
import com.ahn.settlement.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody @Valid RefundRecordRequest request) {
        refundService.register(request);
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.RefundIntegrationTest"
```

Expected: 5 tests PASSED

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/ahn/settlement/dto/request/RefundRecordRequest.java \
        src/main/java/com/ahn/settlement/service/RefundService.java \
        src/main/java/com/ahn/settlement/controller/RefundController.java \
        src/test/java/com/ahn/settlement/integration/RefundIntegrationTest.java
git commit -m "feat: add refund registration API with cumulative validation"
```

---

## Task 10: 크리에이터 월별 정산 API

**Files:**
- Create: `src/main/java/com/ahn/settlement/dto/response/MonthlySettlementResponse.java`
- Create: `src/main/java/com/ahn/settlement/service/SettlementService.java`
- Create: `src/main/java/com/ahn/settlement/controller/SettlementController.java`
- Create: `src/test/java/com/ahn/settlement/integration/SettlementIntegrationTest.java`

- [ ] **Step 1: 실패 테스트 작성 (필수 시나리오 4가지 전부 포함)**

`src/test/java/com/ahn/settlement/integration/SettlementIntegrationTest.java`
```java
package com.ahn.settlement.integration;

import com.ahn.settlement.entity.*;
import com.ahn.settlement.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired CreatorRepository creatorRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired RefundRecordRepository refundRecordRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @BeforeEach
    void setUp() {
        Creator c1 = creatorRepository.save(new Creator("creator-1", "김강사"));
        Creator c2 = creatorRepository.save(new Creator("creator-2", "이강사"));
        creatorRepository.save(new Creator("creator-3", "박강사"));

        Course co1 = courseRepository.save(new Course("course-1", c1, "Spring Boot 입문"));
        Course co2 = courseRepository.save(new Course("course-2", c1, "JPA 실전"));
        Course co3 = courseRepository.save(new Course("course-3", c2, "Kotlin 기초"));

        SaleRecord s1 = saleRecordRepository.save(new SaleRecord("sale-1", co1, "stu1", 50_000L,
                OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST)));
        SaleRecord s2 = saleRecordRepository.save(new SaleRecord("sale-2", co1, "stu2", 50_000L,
                OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, KST)));
        SaleRecord s3 = saleRecordRepository.save(new SaleRecord("sale-3", co2, "stu3", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));
        SaleRecord s4 = saleRecordRepository.save(new SaleRecord("sale-4", co2, "stu4", 80_000L,
                OffsetDateTime.of(2025, 3, 22, 11, 0, 0, 0, KST)));
        SaleRecord s5 = saleRecordRepository.save(new SaleRecord("sale-5", co3, "stu5", 60_000L,
                OffsetDateTime.of(2025, 1, 31, 23, 30, 0, 0, KST)));

        refundRecordRepository.save(new RefundRecord("cancel-1", s3, 80_000L,
                OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST)));
        refundRecordRepository.save(new RefundRecord("cancel-2", s4, 30_000L,
                OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST)));
        refundRecordRepository.save(new RefundRecord("cancel-3", s5, 60_000L,
                OffsetDateTime.of(2025, 2, 1, 0, 30, 0, 0, KST)));
    }

    // ── 시나리오 1: creator-1 / 2025-03 정산 ──────────────────────────────────

    @Test
    void creator1_2025년3월_정산_예정금액은_120000원() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "creator-1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(260_000))
                .andExpect(jsonPath("$.totalRefundAmount").value(110_000))
                .andExpect(jsonPath("$.netSalesAmount").value(150_000))
                .andExpect(jsonPath("$.platformFee").value(30_000))
                .andExpect(jsonPath("$.payoutAmount").value(120_000))
                .andExpect(jsonPath("$.saleCount").value(4))
                .andExpect(jsonPath("$.cancelCount").value(2));
    }

    // ── 시나리오 2: 부분 환불 처리 ───────────────────────────────────────────

    @Test
    void sale4_부분환불_순매출에_50000원_잔존() throws Exception {
        // sale-4(80000) cancel-2(30000) → creator-1의 2025-03 순매출에 50000 잔존
        // 위 시나리오 1에서 이미 검증됨 (netSalesAmount = 150_000 = 50000 + 100000)
        // 부분 환불만 단독 검증: 환불 후에도 판매건이 남아 있어야 함
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "creator-1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRefundAmount").value(110_000))
                .andExpect(jsonPath("$.netSalesAmount").value(150_000)); // 전액환불이 아님
    }

    // ── 시나리오 3: 월 경계 취소 ─────────────────────────────────────────────

    @Test
    void sale5는_1월_판매로_반영() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-2")
                        .param("yearMonth", "2025-01")
                        .header("X-User-Id", "creator-2")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(60_000))
                .andExpect(jsonPath("$.totalRefundAmount").value(0))
                .andExpect(jsonPath("$.saleCount").value(1));
    }

    @Test
    void cancel3는_2월_환불로_반영() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-2")
                        .param("yearMonth", "2025-02")
                        .header("X-User-Id", "creator-2")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(0))
                .andExpect(jsonPath("$.totalRefundAmount").value(60_000))
                .andExpect(jsonPath("$.cancelCount").value(1));
    }

    // ── 시나리오 4: 빈 월 조회 ───────────────────────────────────────────────

    @Test
    void creator3_2025년3월_판매없음_0원응답() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-3")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "creator-3")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(0))
                .andExpect(jsonPath("$.totalRefundAmount").value(0))
                .andExpect(jsonPath("$.payoutAmount").value(0));
    }

    // ── 인증 검사 ────────────────────────────────────────────────────────────

    @Test
    void 다른_크리에이터_정산_조회시_403() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "creator-2")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN_역할은_모든_크리에이터_정산_조회_가능() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.SettlementIntegrationTest"
```

Expected: FAILED — 엔드포인트 없음

- [ ] **Step 3: MonthlySettlementResponse 작성**

`src/main/java/com/ahn/settlement/dto/response/MonthlySettlementResponse.java`
```java
package com.ahn.settlement.dto.response;

public record MonthlySettlementResponse(
        String creatorId,
        String yearMonth,
        long totalSalesAmount,
        long totalRefundAmount,
        long netSalesAmount,
        long platformFee,
        long payoutAmount,
        long saleCount,
        long cancelCount
) {}
```

- [ ] **Step 4: SettlementService 작성**

`src/main/java/com/ahn/settlement/service/SettlementService.java`
```java
package com.ahn.settlement.service;

import com.ahn.settlement.domain.KstDateRange;
import com.ahn.settlement.domain.SettlementCalculator;
import com.ahn.settlement.domain.SettlementResult;
import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.entity.Creator;
import com.ahn.settlement.repository.CreatorRepository;
import com.ahn.settlement.repository.RefundRecordRepository;
import com.ahn.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SaleRecordRepository saleRecordRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final CreatorRepository creatorRepository;

    public MonthlySettlementResponse getMonthlySettlement(String creatorId, YearMonth yearMonth) {
        KstDateRange range = KstDateRange.of(yearMonth);

        long totalSalesAmount = saleRecordRepository.sumAmountByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        long saleCount = saleRecordRepository.countByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        long totalRefundAmount = refundRecordRepository.sumRefundByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        long cancelCount = refundRecordRepository.countByCreatorAndPeriod(
                creatorId, range.start(), range.end());

        SettlementResult result = SettlementCalculator.calculate(totalSalesAmount, totalRefundAmount);

        return new MonthlySettlementResponse(
                creatorId, yearMonth.toString(),
                result.totalSalesAmount(), result.totalRefundAmount(),
                result.netSalesAmount(), result.platformFee(), result.payoutAmount(),
                saleCount, cancelCount);
    }

    public AdminSettlementResponse getAdminSettlement(LocalDate startDate, LocalDate endDate) {
        KstDateRange range = KstDateRange.of(startDate, endDate);

        List<Object[]> salesData = saleRecordRepository.aggregateSalesByCreator(range.start(), range.end());
        List<Object[]> refundData = refundRecordRepository.aggregateRefundsByCreator(range.start(), range.end());

        Map<String, String> nameMap = new HashMap<>();
        Map<String, Long> salesMap = new HashMap<>();

        for (Object[] row : salesData) {
            String creatorId = (String) row[0];
            nameMap.put(creatorId, (String) row[1]);
            salesMap.put(creatorId, ((Number) row[2]).longValue());
        }

        Map<String, Long> refundMap = new HashMap<>();
        for (Object[] row : refundData) {
            String creatorId = (String) row[0];
            refundMap.put(creatorId, ((Number) row[1]).longValue());
        }

        Set<String> allCreatorIds = new LinkedHashSet<>();
        allCreatorIds.addAll(salesMap.keySet());
        allCreatorIds.addAll(refundMap.keySet());

        if (!nameMap.keySet().containsAll(allCreatorIds)) {
            List<Creator> creators = creatorRepository.findAllById(
                    allCreatorIds.stream().filter(id -> !nameMap.containsKey(id)).toList());
            creators.forEach(c -> nameMap.put(c.getId(), c.getName()));
        }

        List<AdminSettlementResponse.CreatorSettlementSummary> summaries = new ArrayList<>();
        long totalPayout = 0;

        for (String creatorId : allCreatorIds) {
            long totalSales = salesMap.getOrDefault(creatorId, 0L);
            long totalRefund = refundMap.getOrDefault(creatorId, 0L);
            SettlementResult result = SettlementCalculator.calculate(totalSales, totalRefund);

            summaries.add(new AdminSettlementResponse.CreatorSettlementSummary(
                    creatorId,
                    nameMap.getOrDefault(creatorId, ""),
                    result.totalSalesAmount(),
                    result.totalRefundAmount(),
                    result.netSalesAmount(),
                    result.platformFee(),
                    result.payoutAmount()));
            totalPayout += result.payoutAmount();
        }

        return new AdminSettlementResponse(
                startDate.toString(), endDate.toString(), summaries, totalPayout);
    }
}
```

- [ ] **Step 5: SettlementController 작성**

`src/main/java/com/ahn/settlement/controller/SettlementController.java`
```java
package com.ahn.settlement.controller;

import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.exception.ForbiddenException;
import com.ahn.settlement.exception.InvalidRequestException;
import com.ahn.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/creators/{creatorId}")
    public MonthlySettlementResponse getMonthlySettlement(
            @PathVariable String creatorId,
            @RequestParam String yearMonth,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"CREATOR".equals(userRole) && !"ADMIN".equals(userRole)) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }
        if ("CREATOR".equals(userRole) && !userId.equals(creatorId)) {
            throw new ForbiddenException("본인의 정산 내역만 조회할 수 있습니다.");
        }

        try {
            return settlementService.getMonthlySettlement(creatorId, YearMonth.parse(yearMonth));
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("yearMonth 형식이 올바르지 않습니다. 예: 2025-03");
        }
    }

    @GetMapping("/admin")
    public AdminSettlementResponse getAdminSettlement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            throw new ForbiddenException("운영자만 접근할 수 있습니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new InvalidRequestException("시작일이 종료일보다 늦을 수 없습니다.");
        }
        return settlementService.getAdminSettlement(startDate, endDate);
    }
}
```

- [ ] **Step 6: AdminSettlementResponse 작성**

`src/main/java/com/ahn/settlement/dto/response/AdminSettlementResponse.java`
```java
package com.ahn.settlement.dto.response;

import java.util.List;

public record AdminSettlementResponse(
        String startDate,
        String endDate,
        List<CreatorSettlementSummary> creatorSettlements,
        long totalPayoutAmount
) {
    public record CreatorSettlementSummary(
            String creatorId,
            String creatorName,
            long totalSalesAmount,
            long totalRefundAmount,
            long netSalesAmount,
            long platformFee,
            long payoutAmount
    ) {}
}
```

- [ ] **Step 7: 테스트 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.SettlementIntegrationTest"
```

Expected: 8 tests PASSED (필수 시나리오 4가지 + 인증 2가지 포함)

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/ahn/settlement/dto/response/MonthlySettlementResponse.java \
        src/main/java/com/ahn/settlement/dto/response/AdminSettlementResponse.java \
        src/main/java/com/ahn/settlement/service/SettlementService.java \
        src/main/java/com/ahn/settlement/controller/SettlementController.java \
        src/test/java/com/ahn/settlement/integration/SettlementIntegrationTest.java
git commit -m "feat: add monthly settlement and admin aggregation API"
```

---

## Task 11: 운영자 집계 API 테스트

**Files:**
- Create: `src/test/java/com/ahn/settlement/integration/AdminSettlementIntegrationTest.java`

- [ ] **Step 1: 테스트 작성**

`src/test/java/com/ahn/settlement/integration/AdminSettlementIntegrationTest.java`
```java
package com.ahn.settlement.integration;

import com.ahn.settlement.entity.*;
import com.ahn.settlement.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSettlementIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired CreatorRepository creatorRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired RefundRecordRepository refundRecordRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @BeforeEach
    void setUp() {
        Creator c1 = creatorRepository.save(new Creator("creator-1", "김강사"));
        Creator c2 = creatorRepository.save(new Creator("creator-2", "이강사"));

        Course co1 = courseRepository.save(new Course("course-1", c1, "Spring Boot 입문"));
        Course co2 = courseRepository.save(new Course("course-2", c1, "JPA 실전"));
        Course co3 = courseRepository.save(new Course("course-3", c2, "Kotlin 기초"));

        SaleRecord s1 = saleRecordRepository.save(new SaleRecord("sale-1", co1, "stu1", 50_000L,
                OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST)));
        SaleRecord s3 = saleRecordRepository.save(new SaleRecord("sale-3", co2, "stu3", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));
        saleRecordRepository.save(new SaleRecord("sale-6", co3, "stu6", 60_000L,
                OffsetDateTime.of(2025, 3, 10, 16, 0, 0, 0, KST)));

        refundRecordRepository.save(new RefundRecord("cancel-1", s3, 80_000L,
                OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST)));
    }

    @Test
    void 운영자_기간별_집계_정상() throws Exception {
        mockMvc.perform(get("/api/settlements/admin")
                        .param("startDate", "2025-03-01")
                        .param("endDate", "2025-03-31")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorSettlements").isArray())
                .andExpect(jsonPath("$.totalPayoutAmount").isNumber());
    }

    @Test
    void CREATOR_역할은_운영자_집계_API_403() throws Exception {
        mockMvc.perform(get("/api/settlements/admin")
                        .param("startDate", "2025-03-01")
                        .param("endDate", "2025-03-31")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 시작일이_종료일보다_늦으면_400() throws Exception {
        mockMvc.perform(get("/api/settlements/admin")
                        .param("startDate", "2025-03-31")
                        .param("endDate", "2025-03-01")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 기간_내_판매없는_크리에이터는_결과에_미포함() throws Exception {
        mockMvc.perform(get("/api/settlements/admin")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-01-31")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorSettlements").isEmpty())
                .andExpect(jsonPath("$.totalPayoutAmount").value(0));
    }
}
```

- [ ] **Step 2: 테스트 실행 및 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.integration.AdminSettlementIntegrationTest"
```

Expected: 4 tests PASSED

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/ahn/settlement/integration/AdminSettlementIntegrationTest.java
git commit -m "test: add admin settlement aggregation integration tests"
```

---

## Task 12: 전체 테스트 통과 확인

- [ ] **Step 1: 전체 테스트 실행**

```bash
./gradlew test
```

Expected: 전체 PASSED, BUILD SUCCESSFUL

- [ ] **Step 2: 애플리케이션 기동 후 수동 smoke test**

```bash
./gradlew bootRun
```

아래 curl 명령으로 확인:

```bash
# creator-1의 2025-03 정산 조회 → payoutAmount: 120000
curl -s "http://localhost:8080/api/settlements/creators/creator-1?yearMonth=2025-03" \
  -H "X-User-Id: creator-1" -H "X-User-Role: CREATOR" | python -m json.tool

# 운영자 기간별 집계
curl -s "http://localhost:8080/api/settlements/admin?startDate=2025-03-01&endDate=2025-03-31" \
  -H "X-User-Role: ADMIN" | python -m json.tool

# 빈 월 조회 → 0원 응답
curl -s "http://localhost:8080/api/settlements/creators/creator-3?yearMonth=2025-03" \
  -H "X-User-Id: creator-3" -H "X-User-Role: CREATOR" | python -m json.tool
```

Expected:
- creator-1 / 2025-03: `payoutAmount: 120000`
- creator-3 / 2025-03: 모든 금액 0

- [ ] **Step 3: 커밋**

```bash
git commit --allow-empty -m "test: verify all tests pass and smoke test complete"
```

---

## Task 13: README.md 작성

**Files:**
- Create: `README.md`

- [ ] **Step 1: README.md 작성**

`README.md` (한국어, 평가자 기준으로 간결하게 작성)

```markdown
# 크리에이터 정산 API

## 프로젝트 개요

크리에이터가 강의를 판매하면 플랫폼 수수료를 제외한 금액을 월별로 정산하는 API입니다.
KST 기준 월 경계 처리, 환불 누적 검증, 정산 계산 정확성에 집중하여 구현했습니다.

## 기술 스택

| 항목 | 선택 |
|------|------|
| Framework | Spring Boot 4.0.6 |
| Language | Java 21 |
| ORM | Spring Data JPA (Hibernate 7) |
| DB | H2 in-memory |
| 빌드 | Gradle |
| 테스트 | JUnit 5, MockMvc |

## 실행 방법

```bash
./gradlew bootRun
```

- 서버: http://localhost:8080
- H2 콘솔: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:settlementdb`
  - 애플리케이션 시작 시 샘플 데이터 자동 삽입

## 테스트 실행 방법

```bash
./gradlew test
```

```bash
# 특정 테스트만 실행
./gradlew test --tests "com.ahn.settlement.integration.SettlementIntegrationTest"
```

## API 목록 및 예시

### 판매 내역 등록
```
POST /api/sales
Content-Type: application/json

{
  "id": "sale-1",
  "courseId": "course-1",
  "studentId": "student-1",
  "amount": 50000,
  "paidAt": "2025-03-05T10:00:00+09:00"
}
```

### 환불 내역 등록
```
POST /api/refunds
Content-Type: application/json

{
  "id": "cancel-1",
  "saleRecordId": "sale-3",
  "refundAmount": 80000,
  "canceledAt": "2025-03-21T10:00:00+09:00"
}
```

### 크리에이터 월별 정산 조회
```
GET /api/settlements/creators/creator-1?yearMonth=2025-03
X-User-Id: creator-1
X-User-Role: CREATOR

응답:
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

### 운영자 기간별 집계
```
GET /api/settlements/admin?startDate=2025-03-01&endDate=2025-03-31
X-User-Role: ADMIN
```

## 데이터 모델 설명

```
Creator(id, name)
  └──< Course(id, creatorId, title)
          └──< SaleRecord(id, courseId, studentId, amount, paidAt)
                  └──< RefundRecord(id, saleRecordId, refundAmount, canceledAt)
```

- `amount`, `refundAmount`: 원 단위 정수 (Long). 소수점 통화 필요 시 BigDecimal 확장 가능.
- `paidAt`, `canceledAt`: OffsetDateTime. DB 내부 저장 방식에 무관하며 KST 기준 구간을 Java에서 계산해 JPQL에 전달.

## 요구사항 해석 및 가정

- 판매 귀속: `paidAt` 기준 월, 환불 귀속: `canceledAt` 기준 월 (독립 적용)
- 월 경계: `[start, end)` 반열린 구간 — 마지막 초 포함/제외 오류 방지
- 빈 월 조회: 판매 없어도 0원 응답 반환 (404 아님)
- 금액: 원 단위 정수로 가정
- 수수료율: 20% 고정 (`FeePolicy` 클래스로 분리하여 확장 가능)

## 설계 결정과 이유

| 결정 | 이유 |
|------|------|
| String ID | 샘플 데이터(creator-1 등)와 일치, 테스트 코드 가독성 향상 |
| Long 금액 | 원 단위 정수 → 부동소수점 오류 없음 |
| OffsetDateTime | KST offset 정보 보존, DB 저장 방식에 무관 |
| Java에서 KST 구간 계산 | DB 함수(MONTH, DATE_TRUNC 등) 미사용 → H2/MySQL/PostgreSQL 모두 호환 |
| Header mock 인증 | 과제 허용 사항, 핵심 평가(정산 정확성, 테스트)에 집중 |
| SettlementRecord 미사용 | 필수 API는 실시간 집계로 충분, 선택 구현 시 추가 예정 |

## 미구현 / 향후 개선 사항

- 정산 상태 관리 (PENDING → CONFIRMED → PAID)
- 중복 정산 방지 (SettlementRecord 추가 시)
- 수수료율 이력 관리 (FeePolicy DB 테이블화)
- CSV 다운로드

## AI 활용 범위

Claude Code (claude-sonnet-4-6)를 사용하여 설계 문서, 구현 계획, 코드 초안을 생성했습니다.
모든 설계 결정(ID 타입, 금액 타입, KST 처리 방식, 인증 방식, 선택 구현 범위 등)은 직접 검토하고 확정했습니다.
생성된 코드는 실행 테스트 및 필수 시나리오 검증을 통해 확인했습니다.
```

- [ ] **Step 2: 커밋**

```bash
git add README.md
git commit -m "docs: add README with API examples, design decisions, and AI usage"
```

---

## Self-Review

**Spec coverage 확인:**

| 요구사항 | 구현 태스크 |
|----------|------------|
| POST /api/sales | Task 8 |
| POST /api/refunds (환불 검증 포함) | Task 9 |
| GET /api/sales (creatorId, 기간 필터) | Task 8 |
| GET /api/settlements/creators/{id}?yearMonth | Task 10 |
| GET /api/settlements/admin?start&end | Task 10 (SettlementController), Task 11 |
| creator-1 / 2025-03 → 120,000원 | Task 10 테스트 |
| 부분 환불 | Task 10 테스트 |
| 월 경계 취소 | Task 10 테스트 |
| 빈 월 조회 0원 응답 | Task 10 테스트 |
| 환불 금액 초과 → 400 | Task 9 테스트 |
| 누적 환불 초과 → 400 | Task 9 테스트 |
| KST 경계값 | Task 2 테스트 (KstDateRangeTest) |
| Header mock 인증 | Task 10 테스트 |
| DataLoader 샘플 데이터 | Task 7 |
| README | Task 13 |

**Placeholder scan:** 없음 — 모든 step에 실제 코드 포함

**Type consistency 확인:**
- `SettlementResult` record → `SettlementCalculator.calculate()` → `SettlementService` → `MonthlySettlementResponse` 타입 일관성 확인
- `KstDateRange.of(YearMonth)`, `KstDateRange.of(LocalDate, LocalDate)` — Task 2, 10, 11에서 동일 시그니처 사용
- `RefundRecord`, `SaleRecord` 생성자 인수 순서 — Task 4, 7, 9 일관성 확인

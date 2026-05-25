# 아키텍처 설계

## 1. 패키지 구조

```
com.ahn.settlement
├── controller
│   ├── SaleController.java
│   ├── RefundController.java
│   └── SettlementController.java
├── service
│   ├── SaleService.java
│   ├── RefundService.java
│   └── SettlementService.java
├── domain
│   ├── SettlementCalculator.java   # 계산 로직
│   ├── FeePolicy.java              # 수수료율 상수 (확장 가능)
│   ├── SettlementResult.java       # 계산 결과 record
│   └── KstDateRange.java           # KST 구간 변환 유틸
├── entity
│   ├── Creator.java
│   ├── Course.java
│   ├── SaleRecord.java
│   └── RefundRecord.java
├── repository
│   ├── CreatorRepository.java
│   ├── CourseRepository.java
│   ├── SaleRecordRepository.java
│   └── RefundRecordRepository.java
├── dto
│   ├── request/
│   └── response/
├── exception
│   ├── GlobalExceptionHandler.java
│   └── (도메인별 예외 클래스)
└── init
    └── DataLoader.java             # 샘플 데이터 삽입
```

---

## 2. 엔티티 관계

```
Creator (id: String)
  └──< Course (id: String, creatorId: String)
          └──< SaleRecord (id: String, courseId: String, amount: Long, paidAt: OffsetDateTime)
                  └──< RefundRecord (id: String, saleRecordId: String, refundAmount: Long, canceledAt: OffsetDateTime)
```

### 연관관계 설정 방침
- JPA `@ManyToOne` / `@OneToMany` 는 필요한 방향만 설정
- 양방향 연관관계는 지양하고 단방향 사용
- 집계 쿼리는 JPQL range 쿼리로 처리 (연관 탐색 최소화)

---

## 3. 레이어별 책임

### Controller
- HTTP 요청/응답 처리
- Header(X-User-Id, X-User-Role) 파싱 및 간단한 역할 검사
- 파라미터 유효성 검사 (@Valid, @RequestParam)
- 비즈니스 로직 없음

### Service
- 유스케이스 조합 및 트랜잭션 경계
- Repository 호출 및 도메인 로직 위임
- 엔티티 검증 (존재 여부, 환불 금액 초과 등)

### Domain
- `SettlementCalculator`: 순매출·수수료·지급액 계산 (순수 Java, 의존성 없음)
- `FeePolicy`: 수수료율 20% 관리 (향후 DB 정책 테이블로 확장 가능한 인터페이스)
- `SettlementResult`: 계산 결과를 담는 불변 record
- `KstDateRange`: KST 기준 startInclusive/endExclusive 구간을 Java에서 계산해 JPQL에 전달하는 유틸
  - `of(YearMonth)` → 월별 정산용 [start, end)
  - `of(LocalDate start, LocalDate end)` → 운영자 기간별 집계용 [start, end+1day)

### Repository
- JPQL 기반 range 쿼리 (`paidAt >= :start AND paidAt < :end`)
- DB 함수(MONTH, YEAR, DATE_TRUNC 등) 미사용 → H2/MySQL/PostgreSQL 호환

---

## 4. KST 월 경계 처리 흐름

```
Client: yearMonth = "2025-03"
         ↓
KstDateRange.of(YearMonth.of(2025, 3))
  start = 2025-03-01T00:00:00+09:00  (startInclusive)
  end   = 2025-04-01T00:00:00+09:00  (endExclusive)
         ↓
JPQL: paidAt >= :start AND paidAt < :end
         ↓
DB: OffsetDateTime 기준 비교 (내부 저장 방식에 무관)
```

DB 내부 저장 포맷(UTC 변환 여부 등)에는 의존하지 않는다.
KST 기준 구간 계산은 항상 Java에서 수행하고, JPQL 파라미터로만 전달한다.

---

## 5. 정산 계산 흐름

```
SettlementService.getMonthlySettlement(creatorId, yearMonth)
  ↓
KstDateRange.of(yearMonth) → [start, end)
  ↓
SaleRecordRepository.sumAmountByCreatorAndPeriod(creatorId, start, end)
  → totalSalesAmount, saleCount
  ↓
RefundRecordRepository.sumRefundByCreatorAndPeriod(creatorId, start, end)
  → totalRefundAmount, cancelCount
  ↓
SettlementCalculator.calculate(totalSales, totalRefund)
  → SettlementResult(netSales, platformFee, payout)
  ↓
응답 DTO 조합 후 반환
```

---

## 6. 환불 검증 흐름

```
RefundService.registerRefund(request)
  ↓
SaleRecordRepository.findById(saleRecordId) → 없으면 404
  ↓
refundAmount <= 0 → 400
refundAmount > saleRecord.amount → 400
  ↓
RefundRecordRepository.sumRefundBySaleRecordId(saleRecordId)
  기존 누적 환불 합계 조회
  ↓
(기존 누적 + 신규 요청) > saleRecord.amount → 400
  ↓
RefundRecord 저장
```

---

## 7. 인증 처리 방식 (Mock)

실제 Spring Security 미사용. Controller에서 헤더를 직접 파싱.

```java
// 예시
String role = request.getHeader("X-User-Role");
String userId = request.getHeader("X-User-Id");

if (!"CREATOR".equals(role)) throw new ForbiddenException();
if (!userId.equals(creatorId)) throw new ForbiddenException();
```

또는 `@RequestHeader`로 파라미터에 바인딩.

---

## 8. 데이터 초기화

`DataLoader` (`ApplicationRunner` 구현체)가 애플리케이션 시작 시 assignment.md의 샘플 데이터를 삽입.
H2 콘솔: `http://localhost:8080/h2-console` (개발 시 접근 가능)

---

## 9. 확장 지점 (Phase 1~4 완료 후 검토)

현재 구현 엔티티는 `Creator`, `Course`, `SaleRecord`, `RefundRecord` 4개이며 `SettlementRecord`는 도입하지 않는다.
Phase 1~4 필수 구현 완료 후 아래 순서로 선택 구현을 검토한다.

| 순위 | 기능 | 확장 방법 |
|------|------|-----------|
| 1 | 중복 정산 방지 | `SettlementRecord` 엔티티 추가, UNIQUE(creatorId, yearMonth) |
| 2 | 정산 상태 관리 | `SettlementRecord.status` 필드 + 상태 전이 API |
| — | 수수료율 이력 | `FeePolicy` → DB 테이블로 전환 (README 향후 개선) |
| — | CSV 다운로드 | `text/csv` 응답 엔드포인트 추가 (README 향후 개선) |

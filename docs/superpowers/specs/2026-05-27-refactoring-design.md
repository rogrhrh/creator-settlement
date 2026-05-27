# 리팩토링 설계 명세

작성일: 2026-05-27

---

## 배경

필수 구현이 완료된 상태에서 코드 품질 개선을 목적으로 리팩토링을 진행한다.
이후 선택 구현(정산 상태 관리, 중복 방지, CSV 다운로드, 수수료율 이력)과 자연스럽게 연결되도록 설계한다.

---

## 리팩토링 항목

### 1. `Object[]` → JPA Projection record 교체

**현재 문제**
- `SaleRecordRepository.aggregateSalesByCreator`, `RefundRecordRepository.aggregateRefundsByCreator`가 `List<Object[]>` 반환
- 서비스에서 `(String) row[0]`, `((Number) row[2]).longValue()` 같은 취약한 캐스팅 필요
- 쿼리 컬럼 순서가 바뀌면 런타임에서야 오류 발생

**변경 내용**
- `dto/query/SaleAggregate.java` 추가 — `record SaleAggregate(String creatorId, String creatorName, long totalAmount, long saleCount)`
- `dto/query/RefundAggregate.java` 추가 — `record RefundAggregate(String creatorId, String creatorName, long totalRefund, long cancelCount)`
- JPQL `new` 생성자 표현식으로 교체
- `RefundAggregate`에 `creatorName` 포함 → 서비스의 `creatorRepository.findAllById()` 폴백 제거

---

### 2. 월별 정산 쿼리 4개 → 2개 통합

**현재 문제**
- `getMonthlySettlement` 호출 시 DB 왕복 4회 (sumAmount, countSales, sumRefund, countCancels)

**변경 내용**
- `dto/query/SalesSummary.java` 추가 — `record SalesSummary(long totalAmount, long saleCount)`
- `dto/query/RefundsSummary.java` 추가 — `record RefundsSummary(long totalRefund, long cancelCount)`
- `SaleRecordRepository.summarizeByCreatorAndPeriod()` — sum + count 한 번에
- `RefundRecordRepository.summarizeByCreatorAndPeriod()` — sum + count 한 번에
- DB 왕복 4회 → 2회

---

### 3. `getAdminSettlement` 서비스 로직 간소화

**현재 문제**
- `Object[]` 캐스팅, 두 Map 수동 병합, 누락 크리에이터 이름 조회(폴백 쿼리) 등 70줄 복잡 로직

**변경 내용**
- Projection 도입(항목 1) 후 `stream().collect(toMap(...))` 패턴으로 교체
- `creatorName`이 두 Aggregate 모두에 포함되므로 폴백 쿼리 완전 제거
- 합산: `summaries.stream().mapToLong(...).sum()`

---

### 4. `YearMonth` 커스텀 컨버터 분리

**현재 문제**
- `SettlementController`에서 `YearMonth.parse(yearMonth)`를 try-catch로 직접 처리
- 컨트롤러가 파싱/예외 변환 책임까지 가짐

**변경 내용**
- `converter/YearMonthConverter.java` 추가 — `Converter<String, YearMonth>` 구현
- `WebMvcConfigurer`에 등록
- 컨트롤러 파라미터: `@RequestParam String yearMonth` → `@RequestParam YearMonth yearMonth`
- 잘못된 형식은 `MethodArgumentTypeMismatchException`으로 처리 → `GlobalExceptionHandler`에서 400 응답

---

### 5. 인증 로직 `AuthValidator` 추출

**현재 문제**
- `SettlementController`에 role 체크 if문이 메서드 내부에 직접 존재
- 다른 컨트롤러에도 유사 패턴 반복 가능성

**변경 내용**
- `auth/AuthValidator.java` 추가 — `validateCreatorAccess(userId, userRole, creatorId)`, `validateAdminAccess(userRole)` 메서드
- 컨트롤러는 `AuthValidator`만 호출하도록 위임
- 예외는 기존 `ForbiddenException` 그대로 사용

---

### 6. `SettlementCalculator.calculate` feeRate 파라미터화

**현재 문제**
- `FeePolicy.FEE_RATE_PERCENT`(20L)를 내부에서 직접 참조
- 이후 선택 구현(수수료율 이력)에서 시점별 요율 주입이 불가능

**변경 내용**
- 시그니처 변경: `calculate(long totalSales, long totalRefund)` → `calculate(long totalSales, long totalRefund, long feeRatePercent)`
- 호출부에서 `FeePolicy.FEE_RATE_PERCENT`를 전달하도록 수정
- 선택 구현 시 DB에서 조회한 이력 요율을 자연스럽게 주입 가능

---

### 7. N+1 문제 해결 — `JOIN FETCH` 추가

**현재 문제**
- `findByCreatorId`, `findByCreatorIdAndPeriod`가 `List<SaleRecord>` 반환
- `SaleRecordResponse.from()`에서 `s.getCourse().getId()`, `s.getCourse().getTitle()` 접근
- `course`가 `FetchType.LAZY`이므로 SaleRecord N개 → Course 조회 N번 추가 발생

**변경 내용**
- 두 쿼리에 `JOIN FETCH s.course` 추가 → 1번의 JOIN 쿼리로 해결
- 집계 쿼리의 암시적 조인(`s.course.creator.id`) → 명시적 `JOIN s.course c JOIN c.creator cr`로 교체

---

### 8. 인덱스 추가

**현재 문제**
- 기간 조회와 JOIN에 자주 사용되는 컬럼에 인덱스 없음

**변경 내용**

| 테이블 | 컬럼 | 용도 |
|--------|------|------|
| `sale_records` | `paid_at` | 기간 WHERE 조건 |
| `sale_records` | `course_id` | JOIN 조건 (FK) |
| `refund_records` | `canceled_at` | 기간 WHERE 조건 |
| `refund_records` | `sale_record_id` | JOIN / SUM 조건 (FK) |

`@Table(indexes = {...})` 어노테이션으로 엔티티에 명시적으로 선언.

---

## 영향 범위 및 변경 없는 항목

| 항목 | 영향 |
|------|------|
| API 스펙 (요청/응답) | 변경 없음 |
| 기존 테스트 | 일부 수정 필요 (SalesSummary/RefundsSummary 도입에 따른 mock 조정) |
| 엔티티/DB 스키마 | 인덱스 어노테이션 추가 (항목 8) |
| `KstDateRange` | 변경 없음 |
| `SettlementResult` | 변경 없음 |

---

## 작업 순서 (권장)

1. N+1 해결 + 인덱스 추가 — 항목 7, 8 (엔티티 변경이므로 선행)
2. Projection record 도입 (SaleAggregate, RefundAggregate) — 항목 1
3. 월별 정산 쿼리 통합 (SalesSummary, RefundsSummary) — 항목 2
4. 서비스 로직 간소화 — 항목 3
5. YearMonth 컨버터 분리 — 항목 4
6. AuthValidator 추출 — 항목 5
7. SettlementCalculator feeRate 파라미터화 — 항목 6

각 항목 완료 후 테스트 전체 통과 확인.

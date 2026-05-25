# 크리에이터 정산 API 과제 정리

## 1. 과제 개요

본 프로젝트는 프로덕트 엔지니어 채용 과제 중 **BE-B. 크리에이터 정산 API**를 구현하는 백엔드 과제이다.

크리에이터가 강의를 판매하면 플랫폼은 판매 금액에서 수수료를 제외한 금액을 크리에이터에게 정산한다.  
크리에이터는 자신의 월별 정산 내역을 조회할 수 있고, 운영자는 특정 기간의 전체 크리에이터 정산 현황을 집계할 수 있다.

수강 취소가 발생하면 이미 계산된 정산 금액에서 환불 금액이 차감되어야 한다.

---

## 2. 선택 과제

| 항목 | 내용 |
|---|---|
| 과제명 | BE-B. 크리에이터 정산 API |
| 유형 | Backend · 데이터 처리 / 정산 |
| 핵심 키워드 | 집계 정확성, 경계값 처리, 쿼리 설계 |
| 필수 기술 | Spring Boot |
| 언어 | Java 또는 Kotlin |
| ORM/Mapper | JPA 또는 MyBatis |
| DB | H2 / MySQL / PostgreSQL 중 선택 |
| 테스트 | BE 과제는 테스트 코드 필수 |

---

## 3. 기술 스택 제약

### 필수

- Spring Boot
- Java 또는 Kotlin
- JPA 또는 MyBatis
- H2 / MySQL / PostgreSQL 중 하나
- 테스트 코드 작성

### 허용

- 인증/인가는 간략화 가능
- `userId`, `creatorId` 등을 헤더 또는 파라미터로 전달 가능
- 실제 결제 시스템 연동 불필요
- 데이터는 API 또는 애플리케이션 시작 시 삽입 가능

---

## 4. AI 사용 정책

AI 도구 사용은 허용된다.

다만 제출물에는 다음 내용이 반영되어야 한다.

- 본인의 판단
- 본인의 수정 사항
- 본인의 검증 결과
- AI 활용 범위에 대한 간단한 설명

README 또는 별도 문서에 AI 활용 범위를 기재해야 한다.

AI 사용 자체는 감점 요소가 아니며, AI 결과물을 얼마나 이해하고 검증했는지가 중요하다.

---

## 5. 제출 조건

### 제출 방식

- GitHub 또는 GitLab Public Repository URL 제출
- `main` 브랜치 기준 실행 가능 상태
- 커밋 히스토리 포함

### 필수 제출물

- Git Repository URL
- README.md
- 소스 코드
- 테스트 코드
- 실행 방법
- AI 사용 내역

### BE 과제 추가 제출물

- API 명세 또는 샘플 요청/응답
- DB 스키마 또는 ERD 설명
- 테스트 실행 방법

---

## 6. README 필수 포함 항목

```md
## 프로젝트 개요
## 기술 스택
## 실행 방법
## 요구사항 해석 및 가정
## 설계 결정과 이유
## 미구현 / 제약사항
## AI 활용 범위

## API 목록 및 예시
## 데이터 모델 설명
## 테스트 실행 방법
```

---

# 7. 도메인 시나리오

## 7.1 배경

크리에이터는 강의를 개설하고 수강생에게 판매한다.

플랫폼은 판매 금액에서 수수료를 제외한 금액을 크리에이터에게 정산한다.

크리에이터는 자신의 월별 정산 내역을 조회할 수 있다.

운영자는 특정 기간의 전체 정산 현황을 집계할 수 있다.

수강 취소가 발생하면 환불 금액은 정산 금액에서 차감된다.

---

# 8. 필수 구현 범위

## 8.1 판매 내역 관리

### 판매 내역 등록

판매 내역 등록 시 다음 정보를 저장한다.

| 필드 | 설명 |
|---|---|
| courseId | 강의 ID |
| studentId | 수강생 ID |
| amount | 결제 금액 |
| paidAt | 결제 일시 |

### 취소 내역 등록

취소 내역 등록 시 다음 정보를 저장한다.

| 필드 | 설명 |
|---|---|
| saleRecordId | 원본 판매 내역 ID |
| refundAmount | 환불 금액 |
| canceledAt | 취소 일시 |

### 판매 내역 목록 조회

판매 내역은 다음 조건으로 조회할 수 있어야 한다.

- 크리에이터별 조회
- 기간 필터 조회

---

## 8.2 크리에이터 월별 정산 조회 API

크리에이터가 특정 월의 정산 내역을 조회한다.

### 요청

| 값 | 설명 | 예시 |
|---|---|---|
| creatorId | 크리에이터 ID | `creator-1` |
| yearMonth | 조회 연월 | `2025-03` |

### 응답 항목

| 항목 | 설명 |
|---|---|
| totalSalesAmount | 해당 월 총 판매 금액 |
| totalRefundAmount | 해당 월 취소/환불 금액 |
| netSalesAmount | 순 판매 금액 |
| platformFee | 플랫폼 수수료 |
| payoutAmount | 정산 예정 금액 |
| saleCount | 판매 건수 |
| cancelCount | 취소 건수 |

### 계산식

```text
순 판매 금액 = 총 판매 금액 - 환불 금액
플랫폼 수수료 = 순 판매 금액 * 20%
정산 예정 금액 = 순 판매 금액 - 플랫폼 수수료
```

---

## 8.3 정산 기간 기준

정산 기간 기준은 다음과 같다.

| 항목 | 기준 |
|---|---|
| 판매 | 결제 완료 일시 기준 |
| 취소/환불 | 취소 일시 기준 |
| 타임존 | KST |
| 월 경계 | 해당 월 1일 00:00:00 ~ 말일 23:59:59 |

예시:

- 2025년 3월 정산 범위  
  `2025-03-01T00:00:00+09:00` 이상  
  `2025-03-31T23:59:59+09:00` 이하

구현 시에는 경계 오류를 피하기 위해 다음과 같은 반열린 구간을 권장한다.

```text
startInclusive <= targetDateTime < endExclusive
```

예시:

```text
2025-03-01T00:00:00+09:00 <= paidAt < 2025-04-01T00:00:00+09:00
```

---

## 8.4 운영자용 정산 집계 API

운영자는 특정 기간 내 전체 크리에이터 정산 현황을 조회할 수 있다.

### 요청

| 값 | 설명 |
|---|---|
| startDate | 조회 시작일 |
| endDate | 조회 종료일 |

### 응답

| 항목 | 설명 |
|---|---|
| creatorSettlements | 크리에이터별 정산 예정 금액 목록 |
| totalPayoutAmount | 전체 정산 예정 금액 합계 |

---

# 9. 선택 구현 범위

다음 항목은 추가 점수 요소이다.

## 9.1 정산 확정 상태 관리

정산 상태를 관리한다.

```text
PENDING -> CONFIRMED -> PAID
```

## 9.2 동일 기간 중복 정산 방지

같은 크리에이터와 같은 기간에 대해 중복 정산이 생성되지 않도록 한다.

## 9.3 정산 내역 다운로드

정산 내역을 다음 중 하나의 형태로 제공한다.

- Excel 다운로드
- CSV 응답

## 9.4 수수료율 변경 이력 관리

기본 수수료율은 20% 고정이다.

다만 수수료율 변경 가능성을 설계에 반영하면 가산점이 있다.

주의할 점:

- 과거 정산은 당시 수수료율을 적용해야 한다.
- 현재 수수료율 변경이 과거 정산 결과를 바꾸면 안 된다.

---

# 10. 비즈니스 규칙

## 10.1 수수료율

```text
플랫폼 수수료율 = 20%
```

일단 고정값으로 구현한다.

다만 변경 가능성을 고려해 다음 중 하나의 방식으로 설계할 수 있다.

- 상수로 분리
- 정책 클래스로 분리
- 수수료 정책 테이블 확장 가능 구조 고려

## 10.2 환불

- 환불 금액은 원본 판매 내역을 참조한다.
- 전액 환불과 부분 환불이 모두 가능하다.
- 환불 금액은 원결제 금액보다 클 수 없어야 한다.
- 취소는 취소 일시 기준 월 정산에 반영한다.

## 10.3 빈 월 조회

판매 내역이 없는 월을 조회해도 일관된 응답을 반환해야 한다.

권장 응답:

```json
{
  "totalSalesAmount": 0,
  "totalRefundAmount": 0,
  "netSalesAmount": 0,
  "platformFee": 0,
  "payoutAmount": 0,
  "saleCount": 0,
  "cancelCount": 0
}
```

---

# 11. 샘플 데이터

## 11.1 크리에이터

```json
[
  { "id": "creator-1", "name": "김강사" },
  { "id": "creator-2", "name": "이강사" },
  { "id": "creator-3", "name": "박강사" }
]
```

## 11.2 강의

```json
[
  { "id": "course-1", "creatorId": "creator-1", "title": "Spring Boot 입문" },
  { "id": "course-2", "creatorId": "creator-1", "title": "JPA 실전" },
  { "id": "course-3", "creatorId": "creator-2", "title": "Kotlin 기초" },
  { "id": "course-4", "creatorId": "creator-3", "title": "MSA 설계" }
]
```

## 11.3 판매 내역

```json
[
  {
    "id": "sale-1",
    "courseId": "course-1",
    "studentId": "student-1",
    "amount": 50000,
    "paidAt": "2025-03-05T10:00:00+09:00"
  },
  {
    "id": "sale-2",
    "courseId": "course-1",
    "studentId": "student-2",
    "amount": 50000,
    "paidAt": "2025-03-15T14:30:00+09:00"
  },
  {
    "id": "sale-3",
    "courseId": "course-2",
    "studentId": "student-3",
    "amount": 80000,
    "paidAt": "2025-03-20T09:00:00+09:00"
  },
  {
    "id": "sale-4",
    "courseId": "course-2",
    "studentId": "student-4",
    "amount": 80000,
    "paidAt": "2025-03-22T11:00:00+09:00"
  },
  {
    "id": "sale-5",
    "courseId": "course-3",
    "studentId": "student-5",
    "amount": 60000,
    "paidAt": "2025-01-31T23:30:00+09:00"
  },
  {
    "id": "sale-6",
    "courseId": "course-3",
    "studentId": "student-6",
    "amount": 60000,
    "paidAt": "2025-03-10T16:00:00+09:00"
  },
  {
    "id": "sale-7",
    "courseId": "course-4",
    "studentId": "student-7",
    "amount": 120000,
    "paidAt": "2025-02-14T10:00:00+09:00"
  }
]
```

## 11.4 취소 내역

과제 검증 시 필요한 취소 데이터는 다음과 같이 구성한다.

```json
[
  {
    "id": "cancel-1",
    "saleRecordId": "sale-3",
    "refundAmount": 80000,
    "canceledAt": "2025-03-21T10:00:00+09:00"
  },
  {
    "id": "cancel-2",
    "saleRecordId": "sale-4",
    "refundAmount": 30000,
    "canceledAt": "2025-03-25T12:00:00+09:00"
  },
  {
    "id": "cancel-3",
    "saleRecordId": "sale-5",
    "refundAmount": 60000,
    "canceledAt": "2025-02-01T00:30:00+09:00"
  }
]
```

---

# 12. 필수 검증 시나리오

## 12.1 creator-1의 2025년 3월 정산

관련 데이터:

- `sale-1`
- `sale-2`
- `sale-3`
- `sale-4`
- `cancel-1`
- `cancel-2`

기대 결과:

| 항목 | 값 |
|---|---:|
| 총 판매 금액 | 260,000 |
| 환불 금액 | 110,000 |
| 순 판매 금액 | 150,000 |
| 플랫폼 수수료 | 30,000 |
| 정산 예정 금액 | 120,000 |

계산:

```text
총 판매 = 50,000 + 50,000 + 80,000 + 80,000 = 260,000
환불 = 80,000 + 30,000 = 110,000
순 판매 = 260,000 - 110,000 = 150,000
수수료 = 150,000 * 20% = 30,000
정산 예정 = 150,000 - 30,000 = 120,000
```

---

## 12.2 부분 환불 처리

관련 데이터:

- `sale-4`
- `cancel-2`

확인 포인트:

- 원결제 금액은 `80,000`
- 환불 금액은 `30,000`
- 전액 환불이 아닌 부분 환불로 처리되어야 한다.
- 순 판매 금액에는 `50,000`이 남아야 한다.

---

## 12.3 월 경계 취소

관련 데이터:

- `sale-5`
- `cancel-3`

확인 포인트:

- `sale-5`는 2025년 1월 판매로 반영된다.
- `cancel-3`은 2025년 2월 취소로 반영된다.
- 판매 일시와 취소 일시는 각각 독립적인 월 기준으로 계산된다.

---

## 12.4 빈 월 조회

관련 데이터:

- `creator-3`
- `2025-03`

확인 포인트:

- 2025년 3월에 creator-3의 판매 내역은 없다.
- 0원 응답을 반환하거나, 사전에 정의한 일관된 정책을 따라야 한다.
- 에러로 처리하지 않는 것을 권장한다.

---

# 13. 추가 테스트 권장 케이스

README에 어떤 케이스를 추가했고 왜 추가했는지 작성하면 좋다.

## 13.1 날짜/시간 경계

- 월 첫날 `00:00:00`
- 월 마지막 날 `23:59:59`
- 다음 달 `00:00:00`
- KST 기준 변환

## 13.2 잘못된 요청

- 존재하지 않는 creatorId
- 존재하지 않는 courseId
- 존재하지 않는 saleRecordId로 취소 요청
- 잘못된 연월 형식
- 시작일이 종료일보다 늦은 기간 조회

## 13.3 금액 검증

- 결제 금액이 0 이하인 경우
- 환불 금액이 0 이하인 경우
- 환불 금액이 원결제 금액보다 큰 경우
- 동일 판매 건에 대해 누적 환불 금액이 원결제 금액을 초과하는 경우

## 13.4 중복/정합성

- 동일 판매 건 중복 등록
- 동일 취소 요청 중복 등록
- 같은 판매 건에 여러 번 부분 환불 발생
- 정산 확정 후 데이터 변경 가능 여부

---

# 14. 설계 시 고려할 점

## 14.1 금액 타입

금액은 부동소수점 타입을 사용하지 않는다.

권장:

- Java: `BigDecimal` 또는 `Long`
- Kotlin: `BigDecimal` 또는 `Long`

원 단위 정수 계산이면 `Long` 사용도 가능하다.

## 14.2 날짜 타입

KST 기준 월 경계 처리가 중요하다.

권장:

- 입력: `OffsetDateTime`
- 내부 기준: `ZonedDateTime` 또는 `OffsetDateTime`
- 월 조회 파라미터: `YearMonth`

## 14.3 조회 성능

정산은 데이터 집계가 핵심이므로 다음을 고려한다.

- creatorId 기준 조회
- paidAt 기준 조회
- canceledAt 기준 조회
- course와 creator 관계 조회
- 집계 쿼리 설계

## 14.4 책임 분리

권장 구조:

```text
controller
  요청/응답 처리

application/service
  유스케이스 조합
  트랜잭션 관리

domain
  정산 계산 규칙
  금액 계산
  상태 전이 규칙

repository
  DB 조회
  집계 쿼리
```

---

# 15. 구현 우선순위 제안

## Phase 1. 기본 도메인 구성

- Creator
- Course
- SaleRecord
- CancelRecord

## Phase 2. 판매/취소 등록

- 판매 등록 API
- 취소 등록 API
- 기본 검증
- 테스트 작성

## Phase 3. 월별 정산 조회

- creatorId + yearMonth 기준 조회
- 판매 합계 계산
- 환불 합계 계산
- 수수료 계산
- 정산 예정 금액 계산
- 필수 검증 시나리오 테스트

## Phase 4. 운영자 집계 API

- 기간 기준 전체 크리에이터 정산 집계
- 크리에이터별 정산 예정 금액 목록
- 전체 합계 계산

## Phase 5. 선택 구현

우선순위 추천:

1. 동일 기간 중복 정산 방지
2. 정산 상태 관리
3. 수수료율 변경 가능성 설계
4. CSV 다운로드

---

# 16. 완료 기준

본 과제는 다음 조건을 만족하면 기본 제출 가능 상태로 본다.

- [ ] 애플리케이션이 로컬에서 실행된다.
- [ ] 판매 내역을 등록할 수 있다.
- [ ] 취소 내역을 등록할 수 있다.
- [ ] 크리에이터별 판매 내역을 조회할 수 있다.
- [ ] 크리에이터별 월별 정산을 조회할 수 있다.
- [ ] 운영자용 기간별 정산 집계를 조회할 수 있다.
- [ ] 샘플 데이터 기반 필수 시나리오가 통과한다.
- [ ] 테스트 코드가 작성되어 있다.
- [ ] README에 실행 방법이 있다.
- [ ] README에 API 예시가 있다.
- [ ] README에 데이터 모델 설명이 있다.
- [ ] README에 요구사항 해석과 가정이 있다.
- [ ] README에 설계 결정과 이유가 있다.
- [ ] README에 AI 활용 범위가 있다.

---

# 17. Claude Code / Superpowers 시작 프롬프트

아래 프롬프트를 Claude Code에서 프로젝트 루트 기준으로 사용할 수 있다.

```text
Use the Superpowers brainstorming workflow.

이 프로젝트는 프로덕트 엔지니어 채용 과제 중 BE-B. 크리에이터 정산 API를 구현하는 백엔드 과제다.

먼저 docs/assignment.md를 읽고 요구사항을 이해해라.
아직 코드는 작성하지 마라.

목표:
- Spring Boot 기반 백엔드 과제 구현
- 정산 계산 정확성
- 월 경계 처리
- 환불 처리
- 운영자용 집계 API
- 테스트 코드 필수
- README 품질 확보

Constraints:
- Do not write code yet.
- Ask clarifying questions first if needed.
- Avoid over-engineering.
- Prioritize one vertical slice.
- Prefer simple, production-like design.
- Document every important design decision.
- Focus on correctness, testability, and maintainability.

먼저 다음 문서를 생성하기 위한 설계 초안을 제안해라.

1. docs/spec.md
2. docs/architecture.md
3. docs/tasks.md
4. docs/decisions.md
5. docs/progress.md

질문은 한 번에 5개 이내로 해라.
```

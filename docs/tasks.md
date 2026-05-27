# 구현 태스크 목록

## Phase 1 — 도메인 기반 구성

- [ ] `application.yaml` 설정 (H2, JPA DDL, H2 콘솔, 타임존)
- [ ] `Creator` 엔티티 + Repository
- [ ] `Course` 엔티티 + Repository
- [ ] `SaleRecord` 엔티티 + Repository
- [ ] `RefundRecord` 엔티티 + Repository
- [ ] `FeePolicy` 클래스 (수수료율 20% 상수)
- [ ] `KstDateRange` 유틸 (YearMonth → KST UTC 구간)
- [ ] `SettlementResult` record
- [ ] `SettlementCalculator` (순매출·수수료·지급액 계산)
- [ ] `DataLoader` (샘플 데이터 삽입)

---

## Phase 2 — 판매/환불 등록 API

- [ ] `POST /api/sales` — 판매 내역 등록
  - 요청 DTO + @Valid 검증
  - CourseId 존재 검증
  - SaleRecord 저장
- [ ] `POST /api/refunds` — 환불 내역 등록
  - SaleRecordId 존재 검증
  - refundAmount 범위 검증
  - 누적 환불 초과 검증
  - RefundRecord 저장
- [ ] `GET /api/sales` — 판매 내역 조회 (creatorId, 기간 필터)
- [ ] 예외 처리 (`GlobalExceptionHandler`, 도메인 예외 클래스)
- [ ] Phase 2 단위/통합 테스트

---

## Phase 3 — 크리에이터 월별 정산 조회 API

- [ ] `GET /api/settlements/creators/{creatorId}?yearMonth=2025-03`
  - Header mock 인증 (X-User-Id, X-User-Role: CREATOR)
  - KST 기준 [start, end) 구간 계산
  - SaleRecord 집계 JPQL 쿼리
  - RefundRecord 집계 JPQL 쿼리
  - SettlementCalculator 호출
  - 빈 월 조회 → 0원 응답
- [ ] 필수 시나리오 테스트
  - creator-1 / 2025-03 → payoutAmount = 120,000원
  - 부분 환불 (sale-4 / cancel-2)
  - 월 경계 취소 (sale-5 / cancel-3)
  - 빈 월 조회 (creator-3 / 2025-03)
  - KST 날짜 경계값 테스트

---

## Phase 4 — 운영자 기간별 정산 집계 API

- [ ] `GET /api/settlements/admin?startDate=2025-03-01&endDate=2025-03-31`
  - Header mock 인증 (X-User-Role: ADMIN)
  - 기간 내 전체 크리에이터별 집계
  - 전체 payoutAmount 합산
- [ ] 운영자 API 테스트
  - 기간별 크리에이터 집계 정확성
  - startDate > endDate → 400

---

## Phase 5 — 품질 및 선택 구현

- [ ] README.md 작성 (한국어, 평가자 기준)
  - 프로젝트 개요, 기술 스택, 실행 방법
  - 요구사항 해석 및 가정
  - 설계 결정과 이유
  - API 목록 및 예시
  - 데이터 모델 설명
  - 테스트 실행 방법
  - AI 활용 범위
  - 미구현/향후 개선 사항
- [ ] 추가 검증 테스트
  - 존재하지 않는 creatorId/courseId/saleRecordId
  - 잘못된 yearMonth 형식
  - amount/refundAmount ≤ 0
  - 시작일 > 종료일
- [x] [선택] 중복 정산 방지 (SettlementRecord + UNIQUE 제약) — 409 Conflict 반환
- [x] [선택] 정산 상태 관리 (PENDING → CONFIRMED → PAID)
- [x] [선택] CSV 다운로드 — RFC 4180 인용 처리, 헤더 인젝션 방지

---

## 완료 기준 체크리스트 (assignment.md 기준)

- [x] 애플리케이션이 로컬에서 실행된다
- [x] 판매 내역을 등록할 수 있다
- [x] 취소(환불) 내역을 등록할 수 있다
- [x] 크리에이터별 판매 내역을 조회할 수 있다
- [x] 크리에이터별 월별 정산을 조회할 수 있다
- [x] 운영자용 기간별 정산 집계를 조회할 수 있다
- [x] 샘플 데이터 기반 필수 시나리오가 통과한다
- [x] 테스트 코드가 작성되어 있다
- [x] README에 실행 방법이 있다
- [x] README에 API 예시가 있다
- [x] README에 데이터 모델 설명이 있다
- [x] README에 요구사항 해석과 가정이 있다
- [x] README에 설계 결정과 이유가 있다
- [x] README에 AI 활용 범위가 있다

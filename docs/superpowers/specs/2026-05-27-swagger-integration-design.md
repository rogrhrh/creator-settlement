# Swagger(OpenAPI) 통합 설계

## 목적

- 로컬 개발/테스트 시 Swagger UI에서 직접 API 호출
- 팀원 및 프론트엔드에 공유할 API 명세 문서화

---

## 파일 구조

```
controller/
  SaleController.java           ← implements SaleControllerSpec (비즈니스 로직만)
  SaleControllerSpec.java       ← Swagger 어노테이션 전담 인터페이스
  RefundController.java         ← implements RefundControllerSpec
  RefundControllerSpec.java
  SettlementController.java     ← implements SettlementControllerSpec
  SettlementControllerSpec.java
  FeePolicyController.java      ← implements FeePolicyControllerSpec
  FeePolicyControllerSpec.java

config/
  SwaggerConfig.java            ← OpenAPI 전역 설정 (SecurityScheme, 서버 정보)
```

---

## 의존성

`build.gradle`에 springdoc-openapi 추가. Spring Boot 4.0.6 호환 버전 사용.

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:<호환버전>'
```

---

## 전역 헤더 (SecurityScheme)

`SwaggerConfig`에서 `X-User-Role`, `X-User-Id`를 `ApiKey` 방식 SecurityScheme으로 등록.
Swagger UI 상단에서 한 번 입력하면 이후 모든 요청에 자동 포함.

헤더가 필요한 엔드포인트 Spec 인터페이스에 `@SecurityRequirement`를 적용.
- `X-User-Role` 필요: SettlementControllerSpec, FeePolicyControllerSpec 전체, 일부 SettlementControllerSpec
- `X-User-Id` 필요: 크리에이터 본인 접근 엔드포인트 (history, monthlySettlement)

---

## 각 Spec 인터페이스 구성

모든 Spec 인터페이스에 공통으로 적용:

| 어노테이션 | 용도 |
|---|---|
| `@Tag` | Swagger UI 그룹명 (예: "판매 기록", "정산") |
| `@Operation` | 엔드포인트 요약(summary) 및 설명(description) |
| `@ApiResponse` | HTTP 상태 코드별 응답 설명 및 예시 |
| `@Parameter` | 쿼리 파라미터, 경로 변수 설명 |

### SaleControllerSpec
- `POST /api/sales` — 판매 기록 등록 (201)
- `GET /api/sales` — 크리에이터별 판매 기록 조회 (creatorId, startDate, endDate)

### RefundControllerSpec
- `POST /api/refunds` — 환불 기록 등록 (201)

### SettlementControllerSpec
- `POST /api/settlements` — 정산 생성 (201, ADMIN 전용)
- `PATCH /api/settlements/{id}/confirm` — 정산 확정 (ADMIN 전용)
- `PATCH /api/settlements/{id}/pay` — 정산 지급 처리 (ADMIN 전용)
- `GET /api/settlements/creators/{creatorId}/history` — 정산 이력 조회
- `GET /api/settlements/creators/{creatorId}/history/csv` — 정산 이력 CSV 다운로드
- `GET /api/settlements/creators/{creatorId}` — 월별 정산 조회 (yearMonth)
- `GET /api/settlements/admin` — 관리자용 전체 정산 조회 (startDate, endDate)

### FeePolicyControllerSpec
- `POST /api/fee-policies` — 수수료율 추가 (201, ADMIN 전용)
- `GET /api/fee-policies` — 수수료율 이력 조회 (ADMIN 전용)

---

## application.yaml 추가 설정

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui/index.html
  api-docs:
    path: /v3/api-docs
```

---

## 접근 URL

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

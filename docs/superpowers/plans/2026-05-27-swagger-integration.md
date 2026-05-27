# Swagger(OpenAPI) 통합 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** springdoc-openapi를 추가하고 `*ControllerSpec` 인터페이스 분리 방식으로 모든 API에 상세 Swagger 어노테이션을 적용한다.

**Architecture:** 각 컨트롤러 옆에 `*ControllerSpec` 인터페이스를 두고 Swagger 어노테이션을 전담시킨다. 컨트롤러는 `implements *ControllerSpec`만 선언하고 비즈니스 로직만 유지한다. 전역 헤더(`X-User-Role`, `X-User-Id`)는 `SwaggerConfig`에서 `SecurityScheme`으로 등록한다.

**Tech Stack:** Spring Boot 4.0.6, springdoc-openapi-starter-webmvc-ui (Spring Boot 4.x 호환 버전), swagger-annotations v3

---

## 파일 맵

| 작업 | 파일 |
|---|---|
| 생성 | `src/main/java/com/ahn/settlement/config/SwaggerConfig.java` |
| 생성 | `src/main/java/com/ahn/settlement/controller/SaleControllerSpec.java` |
| 생성 | `src/main/java/com/ahn/settlement/controller/RefundControllerSpec.java` |
| 생성 | `src/main/java/com/ahn/settlement/controller/SettlementControllerSpec.java` |
| 생성 | `src/main/java/com/ahn/settlement/controller/FeePolicyControllerSpec.java` |
| 생성 | `src/test/java/com/ahn/settlement/swagger/SwaggerDocsTest.java` |
| 수정 | `build.gradle` |
| 수정 | `src/main/resources/application.yaml` |
| 수정 | `src/main/java/com/ahn/settlement/controller/SaleController.java` |
| 수정 | `src/main/java/com/ahn/settlement/controller/RefundController.java` |
| 수정 | `src/main/java/com/ahn/settlement/controller/SettlementController.java` |
| 수정 | `src/main/java/com/ahn/settlement/controller/FeePolicyController.java` |

---

### Task 1: 의존성 추가 및 기본 설정

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.yaml`

> **주의:** Spring Boot 4.0.6은 Spring Framework 7.x 기반이다. springdoc-openapi 버전을 아래 방법으로 확인한다.
> 1. https://github.com/springdoc/springdoc-openapi/releases 에서 최신 릴리즈 확인
> 2. Spring Boot 4.x 지원 여부를 릴리즈 노트에서 확인
> 3. 지원 버전이 없으면 springfox 또는 직접 OpenAPI spec 파일 방식 고려
> springdoc-openapi 2.x 대는 Spring Boot 3.x용이다. Spring Boot 4.x라면 3.x 이상 버전이 필요할 수 있다.

- [ ] **Step 1: `build.gradle`에 springdoc 의존성 추가**

```groovy
dependencies {
    // 기존 의존성 유지 ...
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8'
}
```

> Spring Boot 4.x에서 위 버전이 호환되지 않으면 `./gradlew dependencies` 실행 후 충돌 라이브러리 확인.
> springdoc GitHub에서 Spring Boot 4.x용 버전을 사용한다.

- [ ] **Step 2: `application.yaml`에 springdoc 경로 설정 추가**

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui/index.html
  api-docs:
    path: /v3/api-docs
```

- [ ] **Step 3: 빌드 통과 확인**

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add build.gradle src/main/resources/application.yaml
git commit -m "feat: springdoc-openapi 의존성 및 기본 설정 추가"
```

---

### Task 2: SwaggerConfig — 전역 OpenAPI 설정 및 SecurityScheme 등록

**Files:**
- Create: `src/main/java/com/ahn/settlement/config/SwaggerConfig.java`
- Create: `src/test/java/com/ahn/settlement/swagger/SwaggerDocsTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/ahn/settlement/swagger/SwaggerDocsTest.java`:

```java
package com.ahn.settlement.swagger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_반환_성공() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Creator Settlement API"))
                .andExpect(jsonPath("$.components.securitySchemes.X-User-Role").exists())
                .andExpect(jsonPath("$.components.securitySchemes.X-User-Id").exists());
    }

    @Test
    void swaggerUi_접근_성공() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

```bash
./gradlew test --tests "com.ahn.settlement.swagger.SwaggerDocsTest"
```

Expected: FAIL — `SwaggerConfig` 없으므로 `/v3/api-docs` 404

- [ ] **Step 3: `SwaggerConfig` 구현**

`src/main/java/com/ahn/settlement/config/SwaggerConfig.java`:

```java
package com.ahn.settlement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Creator Settlement API")
                        .description("크리에이터 정산 시스템 API 명세")
                        .version("v1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("X-User-Role", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Role")
                                .description("사용자 역할 (ADMIN / CREATOR)"))
                        .addSecuritySchemes("X-User-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("사용자 ID")));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.ahn.settlement.swagger.SwaggerDocsTest"
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/ahn/settlement/config/SwaggerConfig.java \
        src/test/java/com/ahn/settlement/swagger/SwaggerDocsTest.java
git commit -m "feat: SwaggerConfig 전역 OpenAPI 설정 및 SecurityScheme 등록"
```

---

### Task 3: SaleControllerSpec 인터페이스 + SaleController 연결

**Files:**
- Create: `src/main/java/com/ahn/settlement/controller/SaleControllerSpec.java`
- Modify: `src/main/java/com/ahn/settlement/controller/SaleController.java`

- [ ] **Step 1: `SaleControllerSpec` 인터페이스 작성**

`src/main/java/com/ahn/settlement/controller/SaleControllerSpec.java`:

```java
package com.ahn.settlement.controller;

import com.ahn.settlement.dto.request.SaleRecordRequest;
import com.ahn.settlement.dto.response.SaleRecordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "판매 기록", description = "크리에이터 판매 기록 등록 및 조회")
public interface SaleControllerSpec {

    @Operation(summary = "판매 기록 등록", description = "크리에이터의 강의 판매 기록을 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "판매 기록 등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청")
    })
    SaleRecordResponse register(@RequestBody @Valid SaleRecordRequest request);

    @Operation(summary = "판매 기록 조회", description = "크리에이터 ID로 판매 기록을 조회합니다. 날짜 범위 필터 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청")
    })
    List<SaleRecordResponse> findByCreator(
            @Parameter(description = "크리에이터 ID", required = true) @RequestParam String creatorId,
            @Parameter(description = "조회 시작일 (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);
}
```

- [ ] **Step 2: `SaleController`에 `implements SaleControllerSpec` 추가**

`SaleController.java` 클래스 선언 부분을 수정:

```java
public class SaleController implements SaleControllerSpec {
```

- [ ] **Step 3: 빌드 통과 확인**

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/controller/SaleControllerSpec.java \
        src/main/java/com/ahn/settlement/controller/SaleController.java
git commit -m "feat: SaleControllerSpec 인터페이스 분리 및 Swagger 어노테이션 적용"
```

---

### Task 4: RefundControllerSpec 인터페이스 + RefundController 연결

**Files:**
- Create: `src/main/java/com/ahn/settlement/controller/RefundControllerSpec.java`
- Modify: `src/main/java/com/ahn/settlement/controller/RefundController.java`

- [ ] **Step 1: `RefundControllerSpec` 인터페이스 작성**

`src/main/java/com/ahn/settlement/controller/RefundControllerSpec.java`:

```java
package com.ahn.settlement.controller;

import com.ahn.settlement.dto.request.RefundRecordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "환불 기록", description = "크리에이터 환불 기록 등록")
public interface RefundControllerSpec {

    @Operation(summary = "환불 기록 등록", description = "크리에이터의 강의 환불 기록을 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "환불 기록 등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청")
    })
    void register(@RequestBody @Valid RefundRecordRequest request);
}
```

- [ ] **Step 2: `RefundController`에 `implements RefundControllerSpec` 추가**

```java
public class RefundController implements RefundControllerSpec {
```

- [ ] **Step 3: 빌드 통과 확인**

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/controller/RefundControllerSpec.java \
        src/main/java/com/ahn/settlement/controller/RefundController.java
git commit -m "feat: RefundControllerSpec 인터페이스 분리 및 Swagger 어노테이션 적용"
```

---

### Task 5: SettlementControllerSpec 인터페이스 + SettlementController 연결

**Files:**
- Create: `src/main/java/com/ahn/settlement/controller/SettlementControllerSpec.java`
- Modify: `src/main/java/com/ahn/settlement/controller/SettlementController.java`

> 이 컨트롤러는 엔드포인트별로 필요한 헤더가 다르다.
> - Admin 전용 (create/confirm/pay/admin): `X-User-Role`만 필요
> - 크리에이터 접근 (history/csv/monthly): `X-User-Role` + `X-User-Id` 모두 필요
> `@Parameter(hidden = true)`로 실제 헤더 파라미터를 숨기고 `@SecurityRequirement`로 전역 헤더를 표시한다.

- [ ] **Step 1: `SettlementControllerSpec` 인터페이스 작성**

`src/main/java/com/ahn/settlement/controller/SettlementControllerSpec.java`:

```java
package com.ahn.settlement.controller;

import com.ahn.settlement.dto.request.SettlementCreateRequest;
import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.dto.response.SettlementRecordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Tag(name = "정산", description = "크리에이터 정산 생성, 확정, 지급 및 조회")
public interface SettlementControllerSpec {

    @Operation(summary = "정산 생성", description = "관리자가 크리에이터 정산을 생성합니다. (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "정산 생성 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @SecurityRequirement(name = "X-User-Role")
    SettlementRecordResponse createSettlement(
            @RequestBody @Valid SettlementCreateRequest request,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);

    @Operation(summary = "정산 확정", description = "관리자가 정산을 확정합니다. (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정산 확정 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "정산 없음")
    })
    @SecurityRequirement(name = "X-User-Role")
    SettlementRecordResponse confirmSettlement(
            @Parameter(description = "정산 ID", required = true) @PathVariable String id,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);

    @Operation(summary = "정산 지급 처리", description = "관리자가 정산을 지급 처리합니다. (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "지급 처리 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "정산 없음")
    })
    @SecurityRequirement(name = "X-User-Role")
    SettlementRecordResponse paySettlement(
            @Parameter(description = "정산 ID", required = true) @PathVariable String id,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);

    @Operation(summary = "정산 이력 조회", description = "크리에이터의 정산 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @SecurityRequirement(name = "X-User-Role")
    @SecurityRequirement(name = "X-User-Id")
    List<SettlementRecordResponse> getSettlementHistory(
            @Parameter(description = "크리에이터 ID", required = true) @PathVariable String creatorId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);

    @Operation(summary = "정산 이력 CSV 다운로드", description = "크리에이터의 정산 이력을 CSV 파일로 다운로드합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "다운로드 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @SecurityRequirement(name = "X-User-Role")
    @SecurityRequirement(name = "X-User-Id")
    ResponseEntity<byte[]> getSettlementHistoryCsv(
            @Parameter(description = "크리에이터 ID", required = true) @PathVariable String creatorId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);

    @Operation(summary = "월별 정산 조회", description = "크리에이터의 특정 월 정산 내역을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @SecurityRequirement(name = "X-User-Role")
    @SecurityRequirement(name = "X-User-Id")
    MonthlySettlementResponse getMonthlySettlement(
            @Parameter(description = "크리에이터 ID", required = true) @PathVariable String creatorId,
            @Parameter(description = "조회 연월 (yyyy-MM)", required = true) @RequestParam YearMonth yearMonth,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);

    @Operation(summary = "관리자 전체 정산 조회", description = "관리자가 기간별 전체 정산 현황을 조회합니다. (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "시작일이 종료일보다 늦음"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @SecurityRequirement(name = "X-User-Role")
    AdminSettlementResponse getAdminSettlement(
            @Parameter(description = "조회 시작일 (yyyy-MM-dd)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);
}
```

- [ ] **Step 2: `SettlementController`에 `implements SettlementControllerSpec` 추가**

```java
public class SettlementController implements SettlementControllerSpec {
```

- [ ] **Step 3: 빌드 통과 확인**

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/controller/SettlementControllerSpec.java \
        src/main/java/com/ahn/settlement/controller/SettlementController.java
git commit -m "feat: SettlementControllerSpec 인터페이스 분리 및 Swagger 어노테이션 적용"
```

---

### Task 6: FeePolicyControllerSpec 인터페이스 + FeePolicyController 연결

**Files:**
- Create: `src/main/java/com/ahn/settlement/controller/FeePolicyControllerSpec.java`
- Modify: `src/main/java/com/ahn/settlement/controller/FeePolicyController.java`

- [ ] **Step 1: `FeePolicyControllerSpec` 인터페이스 작성**

`src/main/java/com/ahn/settlement/controller/FeePolicyControllerSpec.java`:

```java
package com.ahn.settlement.controller;

import com.ahn.settlement.dto.request.FeePolicyCreateRequest;
import com.ahn.settlement.dto.response.FeePolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@Tag(name = "수수료 정책", description = "수수료율 등록 및 이력 조회 (ADMIN 전용)")
@SecurityRequirement(name = "X-User-Role")
public interface FeePolicyControllerSpec {

    @Operation(summary = "수수료율 추가", description = "새로운 수수료율을 추가합니다. (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "수수료율 추가 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    FeePolicyResponse addFeePolicy(
            @RequestBody @Valid FeePolicyCreateRequest request,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);

    @Operation(summary = "수수료율 이력 조회", description = "전체 수수료율 변경 이력을 조회합니다. (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    List<FeePolicyResponse> getFeeHistory(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole);
}
```

- [ ] **Step 2: `FeePolicyController`에 `implements FeePolicyControllerSpec` 추가**

```java
public class FeePolicyController implements FeePolicyControllerSpec {
```

- [ ] **Step 3: 빌드 통과 확인**

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/ahn/settlement/controller/FeePolicyControllerSpec.java \
        src/main/java/com/ahn/settlement/controller/FeePolicyController.java
git commit -m "feat: FeePolicyControllerSpec 인터페이스 분리 및 Swagger 어노테이션 적용"
```

---

### Task 7: 전체 테스트 통과 및 Swagger UI 동작 확인

**Files:**
- Modify: `src/test/java/com/ahn/settlement/swagger/SwaggerDocsTest.java` (태그 검증 추가)

- [ ] **Step 1: `SwaggerDocsTest`에 태그 검증 추가**

기존 `SwaggerDocsTest.java`의 `apiDocs_반환_성공` 테스트에 태그 검증 추가:

```java
@Test
void apiDocs_태그_포함_확인() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[?(@.name == '판매 기록')]").exists())
            .andExpect(jsonPath("$.tags[?(@.name == '환불 기록')]").exists())
            .andExpect(jsonPath("$.tags[?(@.name == '정산')]").exists())
            .andExpect(jsonPath("$.tags[?(@.name == '수수료 정책')]").exists());
}
```

- [ ] **Step 2: 전체 테스트 실행**

```bash
./gradlew test
```

Expected: 모든 테스트 PASS

- [ ] **Step 3: 앱 실행 후 Swagger UI 수동 확인**

```bash
./gradlew bootRun
```

브라우저에서 `http://localhost:8080/swagger-ui/index.html` 접속:
- 4개 태그 그룹(판매 기록, 환불 기록, 정산, 수수료 정책) 표시 확인
- Swagger UI 우상단 "Authorize" 버튼 클릭 시 `X-User-Role`, `X-User-Id` 입력 칸 노출 확인
- 각 엔드포인트 `@SecurityRequirement` 자물쇠 아이콘 표시 확인

- [ ] **Step 4: 최종 커밋**

```bash
git add src/test/java/com/ahn/settlement/swagger/SwaggerDocsTest.java
git commit -m "test: Swagger API 문서 태그 검증 테스트 추가"
```

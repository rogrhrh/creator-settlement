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

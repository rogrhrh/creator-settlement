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

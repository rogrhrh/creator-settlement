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

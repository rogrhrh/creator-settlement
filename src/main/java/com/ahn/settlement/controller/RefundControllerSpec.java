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

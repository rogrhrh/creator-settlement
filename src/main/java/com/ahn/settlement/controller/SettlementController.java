package com.ahn.settlement.controller;

import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.exception.ForbiddenException;
import com.ahn.settlement.exception.InvalidRequestException;
import com.ahn.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/creators/{creatorId}")
    public MonthlySettlementResponse getMonthlySettlement(
            @PathVariable String creatorId,
            @RequestParam String yearMonth,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"CREATOR".equals(userRole) && !"ADMIN".equals(userRole)) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }
        if ("CREATOR".equals(userRole) && !userId.equals(creatorId)) {
            throw new ForbiddenException("본인의 정산 내역만 조회할 수 있습니다.");
        }

        try {
            return settlementService.getMonthlySettlement(creatorId, YearMonth.parse(yearMonth));
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("yearMonth 형식이 올바르지 않습니다. 예: 2025-03");
        }
    }

    @GetMapping("/admin")
    public AdminSettlementResponse getAdminSettlement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            throw new ForbiddenException("운영자만 접근할 수 있습니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new InvalidRequestException("시작일이 종료일보다 늦을 수 없습니다.");
        }
        return settlementService.getAdminSettlement(startDate, endDate);
    }
}

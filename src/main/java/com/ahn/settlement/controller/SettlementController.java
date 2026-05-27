package com.ahn.settlement.controller;

import com.ahn.settlement.auth.AuthValidator;
import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.exception.InvalidRequestException;
import com.ahn.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final AuthValidator authValidator;

    @GetMapping("/creators/{creatorId}")
    public MonthlySettlementResponse getMonthlySettlement(
            @PathVariable String creatorId,
            @RequestParam YearMonth yearMonth,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        authValidator.validateCreatorAccess(userId, userRole, creatorId);
        return settlementService.getMonthlySettlement(creatorId, yearMonth);
    }

    @GetMapping("/admin")
    public AdminSettlementResponse getAdminSettlement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-User-Role") String userRole) {

        authValidator.validateAdminAccess(userRole);
        if (startDate.isAfter(endDate)) {
            throw new InvalidRequestException("시작일이 종료일보다 늦을 수 없습니다.");
        }
        return settlementService.getAdminSettlement(startDate, endDate);
    }
}

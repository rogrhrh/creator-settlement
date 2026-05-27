package com.ahn.settlement.controller;

import com.ahn.settlement.auth.AuthValidator;
import com.ahn.settlement.dto.request.SettlementCreateRequest;
import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.dto.response.SettlementRecordResponse;
import com.ahn.settlement.exception.InvalidRequestException;
import com.ahn.settlement.service.SettlementConfirmService;
import com.ahn.settlement.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementConfirmService settlementConfirmService;
    private final AuthValidator authValidator;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementRecordResponse createSettlement(
            @RequestBody @Valid SettlementCreateRequest request,
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateAdminAccess(userRole);
        return settlementConfirmService.create(request);
    }

    @PatchMapping("/{id}/confirm")
    public SettlementRecordResponse confirmSettlement(
            @PathVariable String id,
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateAdminAccess(userRole);
        return settlementConfirmService.confirm(id);
    }

    @PatchMapping("/{id}/pay")
    public SettlementRecordResponse paySettlement(
            @PathVariable String id,
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateAdminAccess(userRole);
        return settlementConfirmService.pay(id);
    }

    @GetMapping("/creators/{creatorId}/history")
    public List<SettlementRecordResponse> getSettlementHistory(
            @PathVariable String creatorId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateCreatorAccess(userId, userRole, creatorId);
        return settlementConfirmService.getHistory(creatorId);
    }

    @GetMapping("/creators/{creatorId}/history/csv")
    public ResponseEntity<byte[]> getSettlementHistoryCsv(
            @PathVariable String creatorId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateCreatorAccess(userId, userRole, creatorId);
        byte[] csv = settlementConfirmService.getHistoryCsv(creatorId)
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + UriUtils.encode("settlement_" + creatorId + ".csv", StandardCharsets.UTF_8))
                .body(csv);
    }

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

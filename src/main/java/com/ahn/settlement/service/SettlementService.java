package com.ahn.settlement.service;

import com.ahn.settlement.domain.FeePolicy;
import com.ahn.settlement.domain.KstDateRange;
import com.ahn.settlement.domain.SettlementCalculator;
import com.ahn.settlement.domain.SettlementResult;
import com.ahn.settlement.dto.query.RefundAggregate;
import com.ahn.settlement.dto.query.RefundsSummary;
import com.ahn.settlement.dto.query.SaleAggregate;
import com.ahn.settlement.dto.query.SalesSummary;
import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.repository.RefundRecordRepository;
import com.ahn.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SaleRecordRepository saleRecordRepository;
    private final RefundRecordRepository refundRecordRepository;

    public MonthlySettlementResponse getMonthlySettlement(String creatorId, YearMonth yearMonth) {
        KstDateRange range = KstDateRange.of(yearMonth);

        SalesSummary sales = saleRecordRepository.summarizeByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        RefundsSummary refunds = refundRecordRepository.summarizeByCreatorAndPeriod(
                creatorId, range.start(), range.end());

        SettlementResult result = SettlementCalculator.calculate(
                sales.totalAmount(), refunds.totalRefund(), FeePolicy.FEE_RATE_PERCENT);

        return new MonthlySettlementResponse(
                creatorId, yearMonth.toString(),
                result.totalSalesAmount(), result.totalRefundAmount(),
                result.netSalesAmount(), result.platformFee(), result.payoutAmount(),
                sales.saleCount(), refunds.cancelCount());
    }

    public AdminSettlementResponse getAdminSettlement(LocalDate startDate, LocalDate endDate) {
        KstDateRange range = KstDateRange.of(startDate, endDate);

        Map<String, SaleAggregate> salesMap = saleRecordRepository
                .aggregateSalesByCreator(range.start(), range.end())
                .stream()
                .collect(Collectors.toMap(SaleAggregate::creatorId, Function.identity()));

        Map<String, RefundAggregate> refundMap = refundRecordRepository
                .aggregateRefundsByCreator(range.start(), range.end())
                .stream()
                .collect(Collectors.toMap(RefundAggregate::creatorId, Function.identity()));

        Set<String> allCreatorIds = new LinkedHashSet<>();
        allCreatorIds.addAll(salesMap.keySet());
        allCreatorIds.addAll(refundMap.keySet());

        List<AdminSettlementResponse.CreatorSettlementSummary> summaries = allCreatorIds.stream()
                .map(id -> buildSummary(id, salesMap, refundMap))
                .toList();

        long totalPayout = summaries.stream()
                .mapToLong(AdminSettlementResponse.CreatorSettlementSummary::payoutAmount)
                .sum();

        return new AdminSettlementResponse(startDate.toString(), endDate.toString(), summaries, totalPayout);
    }

    public SettlementResult calculateSettlement(String creatorId, YearMonth yearMonth) {
        KstDateRange range = KstDateRange.of(yearMonth);
        SalesSummary sales = saleRecordRepository.summarizeByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        RefundsSummary refunds = refundRecordRepository.summarizeByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        return SettlementCalculator.calculate(
                sales.totalAmount(), refunds.totalRefund(), FeePolicy.FEE_RATE_PERCENT);
    }

    private AdminSettlementResponse.CreatorSettlementSummary buildSummary(
            String creatorId,
            Map<String, SaleAggregate> salesMap,
            Map<String, RefundAggregate> refundMap) {

        SaleAggregate sale = salesMap.getOrDefault(creatorId,
                new SaleAggregate(creatorId, "", 0L, 0L));
        RefundAggregate refund = refundMap.getOrDefault(creatorId,
                new RefundAggregate(creatorId, "", 0L, 0L));
        String creatorName = sale.creatorName().isBlank() ? refund.creatorName() : sale.creatorName();

        SettlementResult result = SettlementCalculator.calculate(
                sale.totalAmount(), refund.totalRefund(), FeePolicy.FEE_RATE_PERCENT);

        return new AdminSettlementResponse.CreatorSettlementSummary(
                creatorId, creatorName,
                result.totalSalesAmount(), result.totalRefundAmount(),
                result.netSalesAmount(), result.platformFee(), result.payoutAmount());
    }
}

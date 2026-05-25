package com.ahn.settlement.service;

import com.ahn.settlement.domain.KstDateRange;
import com.ahn.settlement.domain.SettlementCalculator;
import com.ahn.settlement.domain.SettlementResult;
import com.ahn.settlement.dto.response.AdminSettlementResponse;
import com.ahn.settlement.dto.response.MonthlySettlementResponse;
import com.ahn.settlement.entity.Creator;
import com.ahn.settlement.repository.CreatorRepository;
import com.ahn.settlement.repository.RefundRecordRepository;
import com.ahn.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SaleRecordRepository saleRecordRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final CreatorRepository creatorRepository;

    public MonthlySettlementResponse getMonthlySettlement(String creatorId, YearMonth yearMonth) {
        KstDateRange range = KstDateRange.of(yearMonth);

        long totalSalesAmount = saleRecordRepository.sumAmountByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        long saleCount = saleRecordRepository.countByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        long totalRefundAmount = refundRecordRepository.sumRefundByCreatorAndPeriod(
                creatorId, range.start(), range.end());
        long cancelCount = refundRecordRepository.countByCreatorAndPeriod(
                creatorId, range.start(), range.end());

        SettlementResult result = SettlementCalculator.calculate(totalSalesAmount, totalRefundAmount);

        return new MonthlySettlementResponse(
                creatorId, yearMonth.toString(),
                result.totalSalesAmount(), result.totalRefundAmount(),
                result.netSalesAmount(), result.platformFee(), result.payoutAmount(),
                saleCount, cancelCount);
    }

    public AdminSettlementResponse getAdminSettlement(LocalDate startDate, LocalDate endDate) {
        KstDateRange range = KstDateRange.of(startDate, endDate);

        List<Object[]> salesData = saleRecordRepository.aggregateSalesByCreator(range.start(), range.end());
        List<Object[]> refundData = refundRecordRepository.aggregateRefundsByCreator(range.start(), range.end());

        Map<String, String> nameMap = new HashMap<>();
        Map<String, Long> salesMap = new HashMap<>();

        for (Object[] row : salesData) {
            String creatorId = (String) row[0];
            nameMap.put(creatorId, (String) row[1]);
            salesMap.put(creatorId, ((Number) row[2]).longValue());
        }

        Map<String, Long> refundMap = new HashMap<>();
        for (Object[] row : refundData) {
            String creatorId = (String) row[0];
            refundMap.put(creatorId, ((Number) row[1]).longValue());
        }

        Set<String> allCreatorIds = new LinkedHashSet<>();
        allCreatorIds.addAll(salesMap.keySet());
        allCreatorIds.addAll(refundMap.keySet());

        if (!nameMap.keySet().containsAll(allCreatorIds)) {
            List<Creator> creators = creatorRepository.findAllById(
                    allCreatorIds.stream().filter(id -> !nameMap.containsKey(id)).toList());
            creators.forEach(c -> nameMap.put(c.getId(), c.getName()));
        }

        List<AdminSettlementResponse.CreatorSettlementSummary> summaries = new ArrayList<>();
        long totalPayout = 0;

        for (String creatorId : allCreatorIds) {
            long totalSales = salesMap.getOrDefault(creatorId, 0L);
            long totalRefund = refundMap.getOrDefault(creatorId, 0L);
            SettlementResult result = SettlementCalculator.calculate(totalSales, totalRefund);

            summaries.add(new AdminSettlementResponse.CreatorSettlementSummary(
                    creatorId,
                    nameMap.getOrDefault(creatorId, ""),
                    result.totalSalesAmount(),
                    result.totalRefundAmount(),
                    result.netSalesAmount(),
                    result.platformFee(),
                    result.payoutAmount()));
            totalPayout += result.payoutAmount();
        }

        return new AdminSettlementResponse(
                startDate.toString(), endDate.toString(), summaries, totalPayout);
    }
}

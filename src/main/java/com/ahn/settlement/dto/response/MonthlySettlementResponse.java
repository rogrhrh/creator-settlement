package com.ahn.settlement.dto.response;

public record MonthlySettlementResponse(
        String creatorId,
        String yearMonth,
        long totalSalesAmount,
        long totalRefundAmount,
        long netSalesAmount,
        long platformFee,
        long payoutAmount,
        long saleCount,
        long cancelCount
) {}

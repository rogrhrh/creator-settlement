package com.ahn.settlement.dto.response;

import java.util.List;

public record AdminSettlementResponse(
        String startDate,
        String endDate,
        List<CreatorSettlementSummary> creatorSettlements,
        long totalPayoutAmount
) {
    public record CreatorSettlementSummary(
            String creatorId,
            String creatorName,
            long totalSalesAmount,
            long totalRefundAmount,
            long netSalesAmount,
            long platformFee,
            long payoutAmount
    ) {}
}

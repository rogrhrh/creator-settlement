package com.ahn.settlement.domain;

public record SettlementResult(
        long totalSalesAmount,
        long totalRefundAmount,
        long netSalesAmount,
        long platformFee,
        long payoutAmount
) {}

package com.ahn.settlement.domain;

public class SettlementCalculator {

    private SettlementCalculator() {}

    public static SettlementResult calculate(long totalSalesAmount, long totalRefundAmount) {
        long netSalesAmount = totalSalesAmount - totalRefundAmount;
        long platformFee = FeePolicy.calculateFee(netSalesAmount);
        long payoutAmount = netSalesAmount - platformFee;
        return new SettlementResult(totalSalesAmount, totalRefundAmount, netSalesAmount, platformFee, payoutAmount);
    }
}

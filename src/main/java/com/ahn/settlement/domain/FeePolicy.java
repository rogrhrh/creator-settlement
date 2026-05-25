package com.ahn.settlement.domain;

public class FeePolicy {

    public static final long FEE_RATE_PERCENT = 20;

    private FeePolicy() {}

    public static long calculateFee(long netSalesAmount) {
        return netSalesAmount * FEE_RATE_PERCENT / 100;
    }
}

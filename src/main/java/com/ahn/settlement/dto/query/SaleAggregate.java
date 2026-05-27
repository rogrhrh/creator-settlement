package com.ahn.settlement.dto.query;

public record SaleAggregate(
        String creatorId,
        String creatorName,
        long totalAmount,
        long saleCount
) {}

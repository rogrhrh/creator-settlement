package com.ahn.settlement.dto.query;

public record RefundAggregate(
        String creatorId,
        String creatorName,
        long totalRefund,
        long cancelCount
) {}

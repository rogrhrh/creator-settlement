package com.ahn.settlement.dto.response;

import com.ahn.settlement.entity.SettlementRecord;

import java.time.OffsetDateTime;

public record SettlementRecordResponse(
        String id,
        String creatorId,
        String yearMonth,
        String status,
        long totalSalesAmount,
        long totalRefundAmount,
        long netSalesAmount,
        long platformFee,
        long payoutAmount,
        long feeRatePercent,
        OffsetDateTime createdAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime paidAt
) {
    public static SettlementRecordResponse from(SettlementRecord record) {
        return new SettlementRecordResponse(
                record.getId(),
                record.getCreatorId(),
                record.getYearMonth(),
                record.getStatus().name(),
                record.getTotalSalesAmount(),
                record.getTotalRefundAmount(),
                record.getNetSalesAmount(),
                record.getPlatformFee(),
                record.getPayoutAmount(),
                record.getFeeRatePercent(),
                record.getCreatedAt(),
                record.getConfirmedAt(),
                record.getPaidAt()
        );
    }
}

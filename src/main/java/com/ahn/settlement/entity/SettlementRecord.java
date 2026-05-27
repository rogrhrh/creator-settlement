package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "settlement_records",
        indexes = { @Index(name = "idx_settlement_records_creator_id", columnList = "creator_id") },
        uniqueConstraints = { @UniqueConstraint(name = "uq_settlement_creator_year_month",
                columnNames = {"creator_id", "year_month"}) }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementRecord {

    @Id
    private String id;

    @Column(name = "creator_id", nullable = false)
    private String creatorId;

    @Column(name = "year_month", nullable = false)
    private String yearMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(nullable = false)
    private Long totalSalesAmount;

    @Column(nullable = false)
    private Long totalRefundAmount;

    @Column(nullable = false)
    private Long netSalesAmount;

    @Column(nullable = false)
    private Long platformFee;

    @Column(nullable = false)
    private Long payoutAmount;

    @Column(nullable = false)
    private Long feeRatePercent;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime confirmedAt;

    @Column
    private OffsetDateTime paidAt;

    public SettlementRecord(String id, String creatorId, String yearMonth,
                            long totalSalesAmount, long totalRefundAmount,
                            long netSalesAmount, long platformFee, long payoutAmount,
                            long feeRatePercent) {
        this.id = id;
        this.creatorId = creatorId;
        this.yearMonth = yearMonth;
        this.status = SettlementStatus.PENDING;
        this.totalSalesAmount = totalSalesAmount;
        this.totalRefundAmount = totalRefundAmount;
        this.netSalesAmount = netSalesAmount;
        this.platformFee = platformFee;
        this.payoutAmount = payoutAmount;
        this.feeRatePercent = feeRatePercent;
        this.createdAt = OffsetDateTime.now();
    }

    public void confirm() {
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = OffsetDateTime.now();
    }

    public void pay() {
        this.status = SettlementStatus.PAID;
        this.paidAt = OffsetDateTime.now();
    }
}

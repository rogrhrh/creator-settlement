package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "refund_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRecord {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_record_id", nullable = false)
    private SaleRecord saleRecord;

    @Column(nullable = false)
    private Long refundAmount;

    @Column(nullable = false)
    private OffsetDateTime canceledAt;

    public RefundRecord(String id, SaleRecord saleRecord, Long refundAmount, OffsetDateTime canceledAt) {
        this.id = id;
        this.saleRecord = saleRecord;
        this.refundAmount = refundAmount;
        this.canceledAt = canceledAt;
    }
}

package com.ahn.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
    name = "fee_policy_history",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_fee_policy_effective_from",
        columnNames = "effective_from"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeePolicyHistory {

    @Id
    private String id;

    @Column(nullable = false)
    private Long ratePercent;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    public FeePolicyHistory(String id, Long ratePercent, LocalDate effectiveFrom) {
        this.id = id;
        this.ratePercent = ratePercent;
        this.effectiveFrom = effectiveFrom;
    }
}

package com.ahn.settlement.dto.response;

import com.ahn.settlement.entity.FeePolicyHistory;

import java.time.LocalDate;

public record FeePolicyResponse(String id, Long ratePercent, LocalDate effectiveFrom) {

    public static FeePolicyResponse from(FeePolicyHistory h) {
        return new FeePolicyResponse(h.getId(), h.getRatePercent(), h.getEffectiveFrom());
    }
}

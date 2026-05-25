package com.ahn.settlement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record RefundRecordRequest(
        @NotBlank String id,
        @NotBlank String saleRecordId,
        @NotNull @Positive Long refundAmount,
        @NotNull OffsetDateTime canceledAt
) {}

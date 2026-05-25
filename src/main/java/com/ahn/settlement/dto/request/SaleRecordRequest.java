package com.ahn.settlement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record SaleRecordRequest(
        @NotBlank String id,
        @NotBlank String courseId,
        @NotBlank String studentId,
        @NotNull @Positive Long amount,
        @NotNull OffsetDateTime paidAt
) {}

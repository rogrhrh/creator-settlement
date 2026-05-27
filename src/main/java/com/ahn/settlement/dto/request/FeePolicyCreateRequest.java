package com.ahn.settlement.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FeePolicyCreateRequest(
    @NotNull @Min(0) Long ratePercent,
    @NotNull LocalDate effectiveFrom
) {}

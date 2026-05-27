package com.ahn.settlement.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SettlementCreateRequest(
        @NotBlank String creatorId,
        @NotBlank String yearMonth
) {}

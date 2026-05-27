package com.ahn.settlement.controller;

import com.ahn.settlement.auth.AuthValidator;
import com.ahn.settlement.dto.request.FeePolicyCreateRequest;
import com.ahn.settlement.dto.response.FeePolicyResponse;
import com.ahn.settlement.service.FeePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fee-policies")
@RequiredArgsConstructor
public class FeePolicyController {

    private final FeePolicyService feePolicyService;
    private final AuthValidator authValidator;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeePolicyResponse addFeePolicy(
            @RequestBody @Valid FeePolicyCreateRequest request,
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateAdminAccess(userRole);
        return FeePolicyResponse.from(
            feePolicyService.add(request.ratePercent(), request.effectiveFrom()));
    }

    @GetMapping
    public List<FeePolicyResponse> getFeeHistory(
            @RequestHeader("X-User-Role") String userRole) {
        authValidator.validateAdminAccess(userRole);
        return feePolicyService.getHistory().stream()
            .map(FeePolicyResponse::from)
            .toList();
    }
}

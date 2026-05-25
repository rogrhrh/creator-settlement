package com.ahn.settlement.controller;

import com.ahn.settlement.dto.request.RefundRecordRequest;
import com.ahn.settlement.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody @Valid RefundRecordRequest request) {
        refundService.register(request);
    }
}

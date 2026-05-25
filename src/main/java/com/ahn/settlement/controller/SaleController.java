package com.ahn.settlement.controller;

import com.ahn.settlement.dto.request.SaleRecordRequest;
import com.ahn.settlement.dto.response.SaleRecordResponse;
import com.ahn.settlement.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleRecordResponse register(@RequestBody @Valid SaleRecordRequest request) {
        return saleService.register(request);
    }

    @GetMapping
    public List<SaleRecordResponse> findByCreator(
            @RequestParam String creatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return saleService.findByCreator(creatorId, startDate, endDate);
    }
}

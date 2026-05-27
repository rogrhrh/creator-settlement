package com.ahn.settlement.service;

import com.ahn.settlement.domain.FeePolicy;
import com.ahn.settlement.domain.SettlementResult;
import com.ahn.settlement.dto.request.SettlementCreateRequest;
import com.ahn.settlement.dto.response.SettlementRecordResponse;
import com.ahn.settlement.entity.SettlementRecord;
import com.ahn.settlement.entity.SettlementStatus;
import com.ahn.settlement.exception.InvalidRequestException;
import com.ahn.settlement.exception.ResourceNotFoundException;
import com.ahn.settlement.repository.CreatorRepository;
import com.ahn.settlement.repository.SettlementRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementConfirmService {

    private final SettlementRecordRepository settlementRecordRepository;
    private final CreatorRepository creatorRepository;
    private final SettlementService settlementService;

    @Transactional
    public SettlementRecordResponse create(SettlementCreateRequest request) {
        creatorRepository.findById(request.creatorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "크리에이터를 찾을 수 없습니다: " + request.creatorId()));

        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(request.yearMonth());
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("yearMonth 형식이 올바르지 않습니다. 예: 2025-03");
        }

        SettlementResult result = settlementService.calculateSettlement(request.creatorId(), yearMonth);

        SettlementRecord record = new SettlementRecord(
                UUID.randomUUID().toString(),
                request.creatorId(),
                request.yearMonth(),
                result.totalSalesAmount(),
                result.totalRefundAmount(),
                result.netSalesAmount(),
                result.platformFee(),
                result.payoutAmount(),
                FeePolicy.FEE_RATE_PERCENT
        );

        return SettlementRecordResponse.from(settlementRecordRepository.save(record));
    }

    @Transactional
    public SettlementRecordResponse confirm(String id) {
        SettlementRecord record = settlementRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("정산 내역을 찾을 수 없습니다: " + id));
        if (record.getStatus() != SettlementStatus.PENDING) {
            throw new InvalidRequestException("PENDING 상태의 정산만 확정할 수 있습니다.");
        }
        record.confirm();
        return SettlementRecordResponse.from(record);
    }

    @Transactional
    public SettlementRecordResponse pay(String id) {
        SettlementRecord record = settlementRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("정산 내역을 찾을 수 없습니다: " + id));
        if (record.getStatus() != SettlementStatus.CONFIRMED) {
            throw new InvalidRequestException("CONFIRMED 상태의 정산만 지급 완료 처리할 수 있습니다.");
        }
        record.pay();
        return SettlementRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<SettlementRecordResponse> getHistory(String creatorId) {
        return settlementRecordRepository.findByCreatorIdOrderByYearMonthDesc(creatorId)
                .stream()
                .map(SettlementRecordResponse::from)
                .toList();
    }
}

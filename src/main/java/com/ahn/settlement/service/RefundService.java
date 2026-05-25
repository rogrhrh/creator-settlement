package com.ahn.settlement.service;

import com.ahn.settlement.dto.request.RefundRecordRequest;
import com.ahn.settlement.entity.RefundRecord;
import com.ahn.settlement.entity.SaleRecord;
import com.ahn.settlement.exception.InvalidRequestException;
import com.ahn.settlement.exception.ResourceNotFoundException;
import com.ahn.settlement.repository.RefundRecordRepository;
import com.ahn.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRecordRepository refundRecordRepository;
    private final SaleRecordRepository saleRecordRepository;

    @Transactional
    public void register(RefundRecordRequest request) {
        SaleRecord saleRecord = saleRecordRepository.findById(request.saleRecordId())
                .orElseThrow(() -> new ResourceNotFoundException("판매 내역을 찾을 수 없습니다: " + request.saleRecordId()));

        if (request.refundAmount() > saleRecord.getAmount()) {
            throw new InvalidRequestException("환불 금액이 원결제 금액을 초과할 수 없습니다.");
        }

        long alreadyRefunded = refundRecordRepository.sumRefundBySaleRecordId(request.saleRecordId());
        if (alreadyRefunded + request.refundAmount() > saleRecord.getAmount()) {
            throw new InvalidRequestException("누적 환불 금액이 원결제 금액을 초과할 수 없습니다.");
        }

        refundRecordRepository.save(new RefundRecord(
                request.id(), saleRecord, request.refundAmount(), request.canceledAt()));
    }
}

package com.ahn.settlement.dto.response;

import com.ahn.settlement.entity.SaleRecord;

import java.time.OffsetDateTime;

public record SaleRecordResponse(
        String id,
        String courseId,
        String courseTitle,
        String studentId,
        Long amount,
        OffsetDateTime paidAt
) {
    public static SaleRecordResponse from(SaleRecord s) {
        return new SaleRecordResponse(
                s.getId(),
                s.getCourse().getId(),
                s.getCourse().getTitle(),
                s.getStudentId(),
                s.getAmount(),
                s.getPaidAt()
        );
    }
}

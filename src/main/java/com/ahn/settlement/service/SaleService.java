package com.ahn.settlement.service;

import com.ahn.settlement.domain.KstDateRange;
import com.ahn.settlement.dto.request.SaleRecordRequest;
import com.ahn.settlement.dto.response.SaleRecordResponse;
import com.ahn.settlement.entity.Course;
import com.ahn.settlement.entity.SaleRecord;
import com.ahn.settlement.exception.ResourceNotFoundException;
import com.ahn.settlement.repository.CourseRepository;
import com.ahn.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRecordRepository saleRecordRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public SaleRecordResponse register(SaleRecordRequest request) {
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("강의를 찾을 수 없습니다: " + request.courseId()));
        SaleRecord saleRecord = new SaleRecord(
                request.id(), course, request.studentId(), request.amount(), request.paidAt());
        saleRecordRepository.save(saleRecord);
        return SaleRecordResponse.from(saleRecord);
    }

    @Transactional(readOnly = true)
    public List<SaleRecordResponse> findByCreator(String creatorId, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            KstDateRange range = KstDateRange.of(startDate, endDate);
            return saleRecordRepository.findByCreatorIdAndPeriod(creatorId, range.start(), range.end())
                    .stream().map(SaleRecordResponse::from).toList();
        }
        return saleRecordRepository.findByCreatorId(creatorId)
                .stream().map(SaleRecordResponse::from).toList();
    }
}

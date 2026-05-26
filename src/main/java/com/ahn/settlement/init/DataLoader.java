package com.ahn.settlement.init;

import com.ahn.settlement.entity.*;
import com.ahn.settlement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements ApplicationRunner {

    private final CreatorRepository creatorRepository;
    private final CourseRepository courseRepository;
    private final SaleRecordRepository saleRecordRepository;
    private final RefundRecordRepository refundRecordRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Creator c1 = creatorRepository.save(new Creator("creator-1", "김강사"));
        Creator c2 = creatorRepository.save(new Creator("creator-2", "이강사"));
        Creator c3 = creatorRepository.save(new Creator("creator-3", "박강사"));

        Course co1 = courseRepository.save(new Course("course-1", c1, "Spring Boot 입문"));
        Course co2 = courseRepository.save(new Course("course-2", c1, "JPA 실전"));
        Course co3 = courseRepository.save(new Course("course-3", c2, "Kotlin 기초"));
        Course co4 = courseRepository.save(new Course("course-4", c3, "MSA 설계"));

        SaleRecord s1 = saleRecordRepository.save(new SaleRecord("sale-1", co1, "student-1", 50_000L,
                OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST)));
        SaleRecord s2 = saleRecordRepository.save(new SaleRecord("sale-2", co1, "student-2", 50_000L,
                OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, KST)));
        SaleRecord s3 = saleRecordRepository.save(new SaleRecord("sale-3", co2, "student-3", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));
        SaleRecord s4 = saleRecordRepository.save(new SaleRecord("sale-4", co2, "student-4", 80_000L,
                OffsetDateTime.of(2025, 3, 22, 11, 0, 0, 0, KST)));
        SaleRecord s5 = saleRecordRepository.save(new SaleRecord("sale-5", co3, "student-5", 60_000L,
                OffsetDateTime.of(2025, 1, 31, 23, 30, 0, 0, KST)));
        saleRecordRepository.save(new SaleRecord("sale-6", co3, "student-6", 60_000L,
                OffsetDateTime.of(2025, 3, 10, 16, 0, 0, 0, KST)));
        saleRecordRepository.save(new SaleRecord("sale-7", co4, "student-7", 120_000L,
                OffsetDateTime.of(2025, 2, 14, 10, 0, 0, 0, KST)));

        refundRecordRepository.save(new RefundRecord("cancel-1", s3, 80_000L,
                OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST)));
        refundRecordRepository.save(new RefundRecord("cancel-2", s4, 30_000L,
                OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST)));
        refundRecordRepository.save(new RefundRecord("cancel-3", s5, 60_000L,
                OffsetDateTime.of(2025, 2, 1, 0, 30, 0, 0, KST)));

        log.info("샘플 데이터 로드 완료");
    }
}

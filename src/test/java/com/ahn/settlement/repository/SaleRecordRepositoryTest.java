package com.ahn.settlement.repository;

import com.ahn.settlement.entity.Course;
import com.ahn.settlement.entity.Creator;
import com.ahn.settlement.entity.SaleRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SaleRecordRepositoryTest {

    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CreatorRepository creatorRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private Creator creator;
    private Course course;

    @BeforeEach
    void setUp() {
        creator = creatorRepository.save(new Creator("creator-1", "김강사"));
        course = courseRepository.save(new Course("course-1", creator, "Spring Boot 입문"));
    }

    @Test
    void 기간_내_판매금액_합산() {
        OffsetDateTime start = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);

        saleRecordRepository.save(new SaleRecord("s1", course, "stu1", 50_000L,
                OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST)));
        saleRecordRepository.save(new SaleRecord("s2", course, "stu2", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));

        Long sum = saleRecordRepository.sumAmountByCreatorAndPeriod("creator-1", start, end);

        assertThat(sum).isEqualTo(130_000L);
    }

    @Test
    void 기간_외_판매는_합산_제외() {
        OffsetDateTime start = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);

        saleRecordRepository.save(new SaleRecord("s3", course, "stu3", 60_000L,
                OffsetDateTime.of(2025, 2, 28, 23, 59, 59, 0, KST)));

        Long sum = saleRecordRepository.sumAmountByCreatorAndPeriod("creator-1", start, end);

        assertThat(sum).isZero();
    }

    @Test
    void 판매없는_기간_조회시_0반환() {
        OffsetDateTime start = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);

        Long sum = saleRecordRepository.sumAmountByCreatorAndPeriod("creator-1", start, end);

        assertThat(sum).isZero();
    }
}

package com.ahn.settlement.repository;

import com.ahn.settlement.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RefundRecordRepositoryTest {

    @Autowired RefundRecordRepository refundRecordRepository;
    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CreatorRepository creatorRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private SaleRecord sale;

    @BeforeEach
    void setUp() {
        Creator creator = creatorRepository.save(new Creator("c1", "테스터"));
        Course course = courseRepository.save(new Course("co1", creator, "테스트 강의"));
        sale = saleRecordRepository.save(new SaleRecord("s1", course, "stu1", 80_000L,
                OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));
    }

    @Test
    void 판매건별_환불합계_조회() {
        refundRecordRepository.save(new RefundRecord("r1", sale, 30_000L,
                OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST)));

        Long sum = refundRecordRepository.sumRefundBySaleRecordId("s1");

        assertThat(sum).isEqualTo(30_000L);
    }

    @Test
    void 환불없는_판매건_합계는_0() {
        Long sum = refundRecordRepository.sumRefundBySaleRecordId("s1");
        assertThat(sum).isZero();
    }

    @Test
    void 기간_내_크리에이터_환불합계() {
        OffsetDateTime start = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime end = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);

        refundRecordRepository.save(new RefundRecord("r1", sale, 30_000L,
                OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST)));

        Long sum = refundRecordRepository.sumRefundByCreatorAndPeriod("c1", start, end);

        assertThat(sum).isEqualTo(30_000L);
    }

    @Test
    void 월경계_취소는_취소월에만_반영() {
        refundRecordRepository.save(new RefundRecord("r1", sale, 60_000L,
                OffsetDateTime.of(2025, 2, 1, 0, 30, 0, 0, KST)));

        OffsetDateTime marStart = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);
        OffsetDateTime marEnd = OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST);
        OffsetDateTime febStart = OffsetDateTime.of(2025, 2, 1, 0, 0, 0, 0, KST);
        OffsetDateTime febEnd = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST);

        assertThat(refundRecordRepository.sumRefundByCreatorAndPeriod("c1", marStart, marEnd)).isZero();
        assertThat(refundRecordRepository.sumRefundByCreatorAndPeriod("c1", febStart, febEnd)).isEqualTo(60_000L);
    }
}

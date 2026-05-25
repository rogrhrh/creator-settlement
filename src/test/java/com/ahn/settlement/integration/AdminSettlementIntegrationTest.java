package com.ahn.settlement.integration;

import com.ahn.settlement.entity.*;
import com.ahn.settlement.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSettlementIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired CreatorRepository creatorRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired RefundRecordRepository refundRecordRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @BeforeEach
    void setUp() {
        Creator c1 = creatorRepository.findById("creator-1")
                .orElseGet(() -> creatorRepository.save(new Creator("creator-1", "김강사")));
        Creator c2 = creatorRepository.findById("creator-2")
                .orElseGet(() -> creatorRepository.save(new Creator("creator-2", "이강사")));

        Course co1 = courseRepository.findById("course-1")
                .orElseGet(() -> courseRepository.save(new Course("course-1", c1, "Spring Boot 입문")));
        Course co2 = courseRepository.findById("course-2")
                .orElseGet(() -> courseRepository.save(new Course("course-2", c1, "JPA 실전")));
        Course co3 = courseRepository.findById("course-3")
                .orElseGet(() -> courseRepository.save(new Course("course-3", c2, "Kotlin 기초")));

        SaleRecord s1 = saleRecordRepository.findById("sale-1")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-1", co1, "stu1", 50_000L,
                        OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST))));
        SaleRecord s3 = saleRecordRepository.findById("sale-3")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-3", co2, "stu3", 80_000L,
                        OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST))));
        saleRecordRepository.findById("sale-6")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-6", co3, "stu6", 60_000L,
                        OffsetDateTime.of(2025, 3, 10, 16, 0, 0, 0, KST))));

        refundRecordRepository.findById("cancel-1")
                .orElseGet(() -> refundRecordRepository.save(new RefundRecord("cancel-1", s3, 80_000L,
                        OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST))));
    }

    @Test
    void 운영자_기간별_집계_정상() throws Exception {
        mockMvc.perform(get("/api/settlements/admin")
                        .param("startDate", "2025-03-01")
                        .param("endDate", "2025-03-31")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorSettlements").isArray())
                .andExpect(jsonPath("$.totalPayoutAmount").isNumber());
    }

    @Test
    void CREATOR_역할은_운영자_집계_API_403() throws Exception {
        mockMvc.perform(get("/api/settlements/admin")
                        .param("startDate", "2025-03-01")
                        .param("endDate", "2025-03-31")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 시작일이_종료일보다_늦으면_400() throws Exception {
        mockMvc.perform(get("/api/settlements/admin")
                        .param("startDate", "2025-03-31")
                        .param("endDate", "2025-03-01")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 기간_내_판매없는_경우_빈_배열_반환() throws Exception {
        mockMvc.perform(get("/api/settlements/admin")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorSettlements").isEmpty())
                .andExpect(jsonPath("$.totalPayoutAmount").value(0));
    }
}

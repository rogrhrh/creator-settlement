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
class SettlementIntegrationTest {

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
        creatorRepository.findById("creator-3")
                .orElseGet(() -> creatorRepository.save(new Creator("creator-3", "박강사")));

        Course co1 = courseRepository.findById("course-1")
                .orElseGet(() -> courseRepository.save(new Course("course-1", c1, "Spring Boot 입문")));
        Course co2 = courseRepository.findById("course-2")
                .orElseGet(() -> courseRepository.save(new Course("course-2", c1, "JPA 실전")));
        Course co3 = courseRepository.findById("course-3")
                .orElseGet(() -> courseRepository.save(new Course("course-3", c2, "Kotlin 기초")));

        SaleRecord s1 = saleRecordRepository.findById("sale-1")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-1", co1, "stu1", 50_000L,
                        OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST))));
        SaleRecord s2 = saleRecordRepository.findById("sale-2")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-2", co1, "stu2", 50_000L,
                        OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, KST))));
        SaleRecord s3 = saleRecordRepository.findById("sale-3")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-3", co2, "stu3", 80_000L,
                        OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST))));
        SaleRecord s4 = saleRecordRepository.findById("sale-4")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-4", co2, "stu4", 80_000L,
                        OffsetDateTime.of(2025, 3, 22, 11, 0, 0, 0, KST))));
        SaleRecord s5 = saleRecordRepository.findById("sale-5")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-5", co3, "stu5", 60_000L,
                        OffsetDateTime.of(2025, 1, 31, 23, 30, 0, 0, KST))));

        refundRecordRepository.findById("cancel-1")
                .orElseGet(() -> refundRecordRepository.save(new RefundRecord("cancel-1", s3, 80_000L,
                        OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST))));
        refundRecordRepository.findById("cancel-2")
                .orElseGet(() -> refundRecordRepository.save(new RefundRecord("cancel-2", s4, 30_000L,
                        OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST))));
        refundRecordRepository.findById("cancel-3")
                .orElseGet(() -> refundRecordRepository.save(new RefundRecord("cancel-3", s5, 60_000L,
                        OffsetDateTime.of(2025, 2, 1, 0, 30, 0, 0, KST))));
    }

    @Test
    void creator1_2025년3월_정산_예정금액은_120000원() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "creator-1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(260_000))
                .andExpect(jsonPath("$.totalRefundAmount").value(110_000))
                .andExpect(jsonPath("$.netSalesAmount").value(150_000))
                .andExpect(jsonPath("$.platformFee").value(30_000))
                .andExpect(jsonPath("$.payoutAmount").value(120_000))
                .andExpect(jsonPath("$.saleCount").value(4))
                .andExpect(jsonPath("$.cancelCount").value(2));
    }

    @Test
    void sale4_부분환불_순매출에_50000원_잔존() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "creator-1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRefundAmount").value(110_000))
                .andExpect(jsonPath("$.netSalesAmount").value(150_000));
    }

    @Test
    void sale5는_1월_판매로_반영() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-2")
                        .param("yearMonth", "2025-01")
                        .header("X-User-Id", "creator-2")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(60_000))
                .andExpect(jsonPath("$.totalRefundAmount").value(0))
                .andExpect(jsonPath("$.saleCount").value(1));
    }

    @Test
    void cancel3는_2월_환불로_반영() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-2")
                        .param("yearMonth", "2025-02")
                        .header("X-User-Id", "creator-2")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(0))
                .andExpect(jsonPath("$.totalRefundAmount").value(60_000))
                .andExpect(jsonPath("$.cancelCount").value(1));
    }

    @Test
    void creator3_2025년3월_판매없음_0원응답() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-3")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "creator-3")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(0))
                .andExpect(jsonPath("$.totalRefundAmount").value(0))
                .andExpect(jsonPath("$.payoutAmount").value(0));
    }

    @Test
    void 다른_크리에이터_정산_조회시_403() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "creator-2")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN_역할은_모든_크리에이터_정산_조회_가능() throws Exception {
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                        .param("yearMonth", "2025-03")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }
}

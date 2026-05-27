package com.ahn.settlement.integration;

import com.ahn.settlement.entity.FeePolicyHistory;
import com.ahn.settlement.repository.FeePolicyHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeePolicyIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired FeePolicyHistoryRepository feePolicyHistoryRepository;

    @BeforeEach
    void setUp() {
        feePolicyHistoryRepository.deleteAllInBatch();
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-init", 20L, LocalDate.of(2020, 1, 1)));
    }

    @Test
    void POST_수수료율_등록_성공() throws Exception {
        mockMvc.perform(post("/api/fee-policies")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ratePercent\": 15, \"effectiveFrom\": \"2025-06-01\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ratePercent").value(15))
            .andExpect(jsonPath("$.effectiveFrom").value("2025-06-01"));
    }

    @Test
    void POST_중복_effectiveFrom_409_반환() throws Exception {
        mockMvc.perform(post("/api/fee-policies")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ratePercent\": 15, \"effectiveFrom\": \"2020-01-01\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void POST_ADMIN_아닌_역할_403_반환() throws Exception {
        mockMvc.perform(post("/api/fee-policies")
                .header("X-User-Role", "CREATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ratePercent\": 15, \"effectiveFrom\": \"2025-06-01\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void GET_이력_조회_내림차순() throws Exception {
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-new", 10L, LocalDate.of(2025, 4, 1)));

        mockMvc.perform(get("/api/fee-policies")
                .header("X-User-Role", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].effectiveFrom").value("2025-04-01"))
            .andExpect(jsonPath("$[1].effectiveFrom").value("2020-01-01"));
    }

    @Test
    void 수수료율_변경_후_이전_월_정산은_구_수수료율_적용() throws Exception {
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-new", 10L, LocalDate.of(2025, 4, 1)));

        // 2025-03 creator-1: 순매출 150000, 수수료 20% = 30000, 지급 120000
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                .param("yearMonth", "2025-03")
                .header("X-User-Id", "creator-1")
                .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platformFee").value(30000))
            .andExpect(jsonPath("$.payoutAmount").value(120000));
    }

    @Test
    void 수수료율_변경_후_이후_월_정산은_신_수수료율_적용() throws Exception {
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-new", 10L, LocalDate.of(2025, 3, 1)));

        // 2025-03 creator-1: 순매출 150000, 수수료 10% = 15000, 지급 135000
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                .param("yearMonth", "2025-03")
                .header("X-User-Id", "creator-1")
                .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platformFee").value(15000))
            .andExpect(jsonPath("$.payoutAmount").value(135000));
    }
}

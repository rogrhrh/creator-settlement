package com.ahn.settlement.integration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementFlowIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void 전체_정산_플로우_시나리오() throws Exception {
        // 단계 1: creator-1 2025-03
        // 판매 4건(260,000) + 전액환불 1건(80,000) + 부분환불 1건(30,000) → 순매출 150,000
        mockMvc.perform(get("/api/settlements/creators/creator-1")
                .param("yearMonth", "2025-03")
                .header("X-User-Id", "creator-1")
                .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSalesAmount").value(260000))
            .andExpect(jsonPath("$.totalRefundAmount").value(110000))
            .andExpect(jsonPath("$.netSalesAmount").value(150000))
            .andExpect(jsonPath("$.platformFee").value(30000))
            .andExpect(jsonPath("$.payoutAmount").value(120000))
            .andExpect(jsonPath("$.saleCount").value(4))
            .andExpect(jsonPath("$.cancelCount").value(2));

        // 단계 2: creator-2 2025-03
        // 판매 1건(60,000), 환불 없음 (cancel-3은 1월 판매에 대한 2월 환불) → 순매출 60,000
        mockMvc.perform(get("/api/settlements/creators/creator-2")
                .param("yearMonth", "2025-03")
                .header("X-User-Id", "creator-2")
                .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSalesAmount").value(60000))
            .andExpect(jsonPath("$.totalRefundAmount").value(0))
            .andExpect(jsonPath("$.netSalesAmount").value(60000))
            .andExpect(jsonPath("$.platformFee").value(12000))
            .andExpect(jsonPath("$.payoutAmount").value(48000))
            .andExpect(jsonPath("$.saleCount").value(1))
            .andExpect(jsonPath("$.cancelCount").value(0));

        // 단계 3: creator-3 2025-02
        // 판매 1건(120,000), 환불 없음 → 순매출 120,000
        mockMvc.perform(get("/api/settlements/creators/creator-3")
                .param("yearMonth", "2025-02")
                .header("X-User-Id", "creator-3")
                .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSalesAmount").value(120000))
            .andExpect(jsonPath("$.totalRefundAmount").value(0))
            .andExpect(jsonPath("$.netSalesAmount").value(120000))
            .andExpect(jsonPath("$.platformFee").value(24000))
            .andExpect(jsonPath("$.payoutAmount").value(96000))
            .andExpect(jsonPath("$.saleCount").value(1))
            .andExpect(jsonPath("$.cancelCount").value(0));

        // 단계 4: creator-2 2025-01
        // 판매 1건(60,000), 환불은 2월에 발생 → 1월 순매출에 환불 반영 안 됨
        mockMvc.perform(get("/api/settlements/creators/creator-2")
                .param("yearMonth", "2025-01")
                .header("X-User-Id", "creator-2")
                .header("X-User-Role", "CREATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSalesAmount").value(60000))
            .andExpect(jsonPath("$.totalRefundAmount").value(0))
            .andExpect(jsonPath("$.netSalesAmount").value(60000))
            .andExpect(jsonPath("$.platformFee").value(12000))
            .andExpect(jsonPath("$.payoutAmount").value(48000))
            .andExpect(jsonPath("$.saleCount").value(1))
            .andExpect(jsonPath("$.cancelCount").value(0));

        // 단계 5: admin 2025-03 전체 집계
        // creator-1(지급 120,000) + creator-2(지급 48,000) = 총 지급 168,000
        // creator-3은 3월 판매 없음 → 집계에 미포함
        mockMvc.perform(get("/api/settlements/admin")
                .param("startDate", "2025-03-01")
                .param("endDate", "2025-03-31")
                .header("X-User-Role", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPayoutAmount").value(168000))
            .andExpect(jsonPath("$.creatorSettlements.length()").value(2))
            .andExpect(jsonPath("$.creatorSettlements[*].payoutAmount", Matchers.hasItems(120000, 48000)));
    }
}

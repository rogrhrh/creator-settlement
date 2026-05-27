package com.ahn.settlement.integration;

import com.ahn.settlement.entity.*;
import com.ahn.settlement.repository.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementConfirmIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreatorRepository creatorRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired RefundRecordRepository refundRecordRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @BeforeEach
    void setUp() {
        Creator creator = creatorRepository.findById("creator-1")
                .orElseGet(() -> creatorRepository.save(new Creator("creator-1", "김강사")));
        Course co1 = courseRepository.findById("course-1")
                .orElseGet(() -> courseRepository.save(new Course("course-1", creator, "Spring Boot 입문")));
        Course co2 = courseRepository.findById("course-2")
                .orElseGet(() -> courseRepository.save(new Course("course-2", creator, "JPA 실전")));

        if (!saleRecordRepository.existsById("sale-1")) {
            SaleRecord s1 = saleRecordRepository.save(new SaleRecord("sale-1", co1, "student-1", 50_000L,
                    OffsetDateTime.of(2025, 3, 5, 10, 0, 0, 0, KST)));
            SaleRecord s2 = saleRecordRepository.save(new SaleRecord("sale-2", co1, "student-2", 50_000L,
                    OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, KST)));
            SaleRecord s3 = saleRecordRepository.save(new SaleRecord("sale-3", co2, "student-3", 80_000L,
                    OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST)));
            SaleRecord s4 = saleRecordRepository.save(new SaleRecord("sale-4", co2, "student-4", 80_000L,
                    OffsetDateTime.of(2025, 3, 22, 11, 0, 0, 0, KST)));
            refundRecordRepository.save(new RefundRecord("cancel-1", s3, 80_000L,
                    OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST)));
            refundRecordRepository.save(new RefundRecord("cancel-2", s4, 30_000L,
                    OffsetDateTime.of(2025, 3, 25, 12, 0, 0, 0, KST)));
        }
    }

    @Test
    void 정산_생성_creator1_2025_03_payoutAmount_120000() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payoutAmount").value(120000))
                .andExpect(jsonPath("$.feeRatePercent").value(20))
                .andExpect(jsonPath("$.creatorId").value("creator-1"))
                .andExpect(jsonPath("$.yearMonth").value("2025-03"));
    }

    @Test
    void PENDING_CONFIRMED_PAID_순차_상태_전이() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        String response = mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(patch("/api/settlements/" + id + "/confirm")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        mockMvc.perform(patch("/api/settlements/" + id + "/pay")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
    }

    @Test
    void PENDING_상태에서_pay_직접_요청시_400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        String response = mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(patch("/api/settlements/" + id + "/pay")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void CONFIRMED_상태에서_confirm_재요청시_400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        String response = mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(patch("/api/settlements/" + id + "/confirm")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/settlements/" + id + "/confirm")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_creatorId_정산_생성시_404() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "not-exist", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void CREATOR_권한으로_정산_생성시_403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", "creator-1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 크리에이터_정산_이력_조회() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/settlements/creators/creator-1/history")
                        .header("X-User-Id", "creator-1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].yearMonth").value("2025-03"));
    }

    @Test
    void 동일_기간_정산_중복_생성시_409() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isConflict());
    }

    @Test
    void 다른_yearMonth는_중복_아님() throws Exception {
        String body1 = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-03"));
        String body2 = objectMapper.writeValueAsString(
                Map.of("creatorId", "creator-1", "yearMonth", "2025-04"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated());
    }
}

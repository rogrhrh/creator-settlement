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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefundIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreatorRepository creatorRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired SaleRecordRepository saleRecordRepository;
    @Autowired RefundRecordRepository refundRecordRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @BeforeEach
    void setUp() {
        Creator c = creatorRepository.findById("creator-1")
                .orElseGet(() -> creatorRepository.save(new Creator("creator-1", "김강사")));
        Course co = courseRepository.findById("course-1")
                .orElseGet(() -> courseRepository.save(new Course("course-1", c, "Spring Boot 입문")));
        saleRecordRepository.findById("sale-1")
                .orElseGet(() -> saleRecordRepository.save(new SaleRecord("sale-1", co, "stu-1", 80_000L,
                        OffsetDateTime.of(2025, 3, 20, 9, 0, 0, 0, KST))));
    }

    @Test
    void 환불_등록_성공() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "cancel-t1",
                "saleRecordId", "sale-1",
                "refundAmount", 30000,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void 존재하지_않는_saleRecordId_환불시_404() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "cancel-t2",
                "saleRecordId", "sale-9999",
                "refundAmount", 30000,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 환불금액이_원결제금액_초과시_400() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "cancel-t3",
                "saleRecordId", "sale-1",
                "refundAmount", 90000,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 누적환불이_원결제금액_초과시_400() throws Exception {
        refundRecordRepository.saveAndFlush(new RefundRecord("cancel-existing",
                saleRecordRepository.findById("sale-1").get(), 60_000L,
                OffsetDateTime.of(2025, 3, 21, 10, 0, 0, 0, KST)));

        Map<String, Object> body = Map.of(
                "id", "cancel-t4",
                "saleRecordId", "sale-1",
                "refundAmount", 30000,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 환불금액_0이하이면_400() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "cancel-t5",
                "saleRecordId", "sale-1",
                "refundAmount", 0,
                "canceledAt", "2025-03-25T12:00:00+09:00"
        );

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}

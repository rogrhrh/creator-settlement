package com.ahn.settlement.integration;

import com.ahn.settlement.entity.Course;
import com.ahn.settlement.entity.Creator;
import com.ahn.settlement.repository.CourseRepository;
import com.ahn.settlement.repository.CreatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaleIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreatorRepository creatorRepository;
    @Autowired CourseRepository courseRepository;

    @BeforeEach
    void setUp() {
        Creator creator = creatorRepository.findById("creator-1")
                .orElseGet(() -> creatorRepository.save(new Creator("creator-1", "김강사")));
        courseRepository.findById("course-1")
                .orElseGet(() -> courseRepository.save(new Course("course-1", creator, "Spring Boot 입문")));
    }

    @Test
    void 판매_등록_성공() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "sale-test-1",
                "courseId", "course-1",
                "studentId", "student-1",
                "amount", 50000,
                "paidAt", "2025-03-05T10:00:00+09:00"
        );

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("sale-test-1"))
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    void 존재하지_않는_courseId_등록시_404() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "sale-test-2",
                "courseId", "course-9999",
                "studentId", "student-1",
                "amount", 50000,
                "paidAt", "2025-03-05T10:00:00+09:00"
        );

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void amount가_0이하이면_400() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "sale-test-3",
                "courseId", "course-1",
                "studentId", "student-1",
                "amount", 0,
                "paidAt", "2025-03-05T10:00:00+09:00"
        );

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest());
    }
}

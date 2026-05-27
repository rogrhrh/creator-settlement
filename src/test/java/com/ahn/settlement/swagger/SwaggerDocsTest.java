package com.ahn.settlement.swagger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_반환_성공() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Creator Settlement API"))
                .andExpect(jsonPath("$.components.securitySchemes.X-User-Role").exists())
                .andExpect(jsonPath("$.components.securitySchemes.X-User-Id").exists());
    }

    @Test
    void swaggerUi_접근_성공() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void apiDocs_태그_포함_확인() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[?(@.name == '판매 기록')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == '환불 기록')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == '정산')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == '수수료 정책')]").exists());
    }
}

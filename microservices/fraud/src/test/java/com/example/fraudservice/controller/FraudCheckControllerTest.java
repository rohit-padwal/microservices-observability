package com.example.fraudservice.controller;

import com.example.fraudservice.config.SecurityConfig;
import com.example.fraudservice.model.FraudCheck;
import com.example.fraudservice.service.FraudDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudCheckController.class)
@Import(SecurityConfig.class)
class FraudCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FraudDetectionService fraudDetectionService;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void searchReturnsPagedFraudDtos() throws Exception {
        FraudCheck check = FraudCheck.builder().orderId(31L).paymentId(42L)
                .amount(new BigDecimal("1200.00")).riskScore(0.72).decision(FraudCheck.Decision.REVIEW).build();
        when(fraudDetectionService.search(eq(FraudCheck.Decision.REVIEW), eq(31L), isNull(),
                any(), isNull(), eq(0), eq(10), eq("riskScore"), eq(Sort.Direction.DESC)))
                .thenReturn(new PageImpl<>(List.of(check), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/fraud-checks").param("decision", "REVIEW").param("orderId", "31")
                        .param("minimumAmount", "1000").param("page", "0").param("size", "10")
                        .param("sort", "riskScore").param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].decision").value("REVIEW"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void fraudApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/fraud-checks")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void fraudStatisticsRequireAdministratorRole() throws Exception {
        mockMvc.perform(get("/api/fraud-checks/statistics")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statisticsEndpointReturnsDecisionCounts() throws Exception {
        when(fraudDetectionService.getStatistics()).thenReturn(
                new FraudDetectionService.FraudStatistics(12, 8, 2, 2, 0.31));

        mockMvc.perform(get("/api/fraud-checks/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChecks").value(12))
                .andExpect(jsonPath("$.averageRiskScore").value(0.31));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void invalidFraudCheckReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/fraud-checks").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanResolveAndDeleteReviewRecords() throws Exception {
        FraudCheck check = FraudCheck.builder().orderId(31L).paymentId(42L)
                .amount(new BigDecimal("1200.00")).riskScore(0.72).decision(FraudCheck.Decision.APPROVE).build();
        when(fraudDetectionService.resolveReview(5L, FraudCheck.Decision.APPROVE)).thenReturn(check);

        mockMvc.perform(patch("/api/fraud-checks/5/decision").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"));
        mockMvc.perform(delete("/api/fraud-checks/5")).andExpect(status().isNoContent());
    }
}
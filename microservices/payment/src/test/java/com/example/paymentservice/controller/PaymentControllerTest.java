package com.example.paymentservice.controller;

import com.example.paymentservice.config.SecurityConfig;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.service.PaymentService;
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
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void pagedSearchReturnsDtoMetadataAndAppliesQuery() throws Exception {
        Payment payment = new Payment();
        payment.setId(8L);
        payment.setOrderId(31L);
        payment.setAmount(new BigDecimal("85.00"));
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setCreatedAt(Instant.parse("2026-09-29T12:00:00Z"));
        when(paymentService.searchPayments(eq(Payment.PaymentStatus.COMPLETED), eq(31L),
                eq(new BigDecimal("50")), isNull(), eq(0), eq(10), eq("amount"), eq(Sort.Direction.ASC)))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/payments").param("status", "COMPLETED").param("orderId", "31")
                        .param("minimumAmount", "50").param("page", "0").param("size", "10")
                        .param("sort", "amount").param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(8))
                .andExpect(jsonPath("$.content[0].amount").value(85.0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void paymentApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/payments")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void statisticsRequireAdministratorRole() throws Exception {
        mockMvc.perform(get("/api/payments/statistics")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statisticsEndpointReturnsGlobalSummary() throws Exception {
        when(paymentService.getStatistics()).thenReturn(
                new PaymentService.PaymentStatistics(9, 1, 6, 2, new BigDecimal("320.00")));

        mockMvc.perform(get("/api/payments/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPayments").value(9))
                .andExpect(jsonPath("$.completedAmount").value(320.0));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void invalidPaymentRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanFailPendingPaymentAndDeletePendingRecord() throws Exception {
        Payment payment = new Payment();
        payment.setId(8L);
        payment.setOrderId(31L);
        payment.setAmount(new BigDecimal("85.00"));
        payment.setStatus(Payment.PaymentStatus.FAILED);
        when(paymentService.updateStatus(8L, Payment.PaymentStatus.FAILED)).thenReturn(payment);

        mockMvc.perform(patch("/api/payments/8/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(delete("/api/payments/8")).andExpect(status().isNoContent());
    }
}
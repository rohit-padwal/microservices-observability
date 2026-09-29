package com.example.notificationservice.controller;

import com.example.notificationservice.config.SecurityConfig;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.service.NotificationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void searchReturnsPagedNotificationDtos() throws Exception {
        Notification notification = Notification.builder().paymentId(62L).orderId(51L)
                .amount(new BigDecimal("45.00")).type("PAYMENT_FAILED").status(Notification.Status.FAILED).build();
        when(notificationService.search(eq(Notification.Status.FAILED), isNull(), eq(51L), eq("failed"),
                any(), isNull(), eq(0), eq(10), eq("createdAt"), eq(Sort.Direction.DESC)))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/notifications").param("status", "FAILED").param("orderId", "51")
                        .param("type", "failed").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void notificationApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void notificationStatisticsRequireAdministratorRole() throws Exception {
        mockMvc.perform(get("/api/notifications/statistics")).andExpect(status().isForbidden());
    }

        @Test
        @WithMockUser(roles = "ADMIN")
        void statisticsEndpointReturnsDeliveryCounts() throws Exception {
                when(notificationService.getStatistics()).thenReturn(new NotificationService.NotificationStatistics(14, 12, 2));

                mockMvc.perform(get("/api/notifications/statistics"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(14))
                                .andExpect(jsonPath("$.failed").value(2));
        }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void invalidNotificationRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCorrectFailedNotificationAndDeleteIt() throws Exception {
        Notification corrected = Notification.builder().paymentId(62L).orderId(51L)
                .amount(new BigDecimal("45.00")).type("PAYMENT_SUCCESS").status(Notification.Status.FAILED).build();
        when(notificationService.updateFailed(7L, 62L, 51L, new BigDecimal("45.00"), "PAYMENT_SUCCESS"))
                .thenReturn(corrected);

        mockMvc.perform(put("/api/notifications/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":62,\"orderId\":51,\"amount\":45.00,\"type\":\"PAYMENT_SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PAYMENT_SUCCESS"));
        mockMvc.perform(delete("/api/notifications/7")).andExpect(status().isNoContent());
    }
}
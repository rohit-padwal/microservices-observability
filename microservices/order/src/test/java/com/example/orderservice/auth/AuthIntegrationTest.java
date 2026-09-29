package com.example.orderservice.auth;

import com.example.orderservice.client.PaymentServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-integration;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.secret=test-only-key-material-with-at-least-32-bytes",
        "security.bootstrap.username=integration-admin",
        "security.bootstrap.password=integration-admin-password"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PaymentServiceClient paymentServiceClient;

    @Test
    void bootstrapHashesPasswordAndJwtProtectsOrderApi() throws Exception {
        AppUser admin = users.findByUsername("integration-admin").orElseThrow();
        assertNotEquals("integration-admin-password", admin.getPassword());
        assertTrue(passwordEncoder.matches("integration-admin-password", admin.getPassword()));

        String adminToken = login("integration-admin", "integration-admin-password");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer malformed.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminProvisionedOperatorCanWorkButCannotProvisionUsers() throws Exception {
        String adminToken = login("integration-admin", "integration-admin-password");
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"integration-operator\",\"password\":\"operator-long-password\"}"))
                .andExpect(status().isOk());

        String operatorToken = login("integration-operator", "operator-long-password");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"forbidden\",\"password\":\"operator-long-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"integration-admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(response.getResponse().getContentAsString());
        assertEquals("Bearer", json.path("tokenType").asText());
        assertFalse(json.has("password"));
        assertFalse(json.has("passwordHash"));
        return json.path("accessToken").asText();
    }

    private record LoginBody(String username, String password) {}
}
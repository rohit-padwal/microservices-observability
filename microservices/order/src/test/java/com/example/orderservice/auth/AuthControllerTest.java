package com.example.orderservice.auth;

import com.example.orderservice.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private AppUserRepository appUserRepository;

    @Test
    void loginReturnsBackendIssuedToken() throws Exception {
        when(authService.login("operator", "correct-password"))
                .thenReturn(new AuthService.LoginResult("signed.jwt.value", Instant.now().plusSeconds(900),
                        "operator", UserRole.OPERATOR));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"operator\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.value"))
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void invalidCredentialsReturn401() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"operator\",\"password\":\"incorrect-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotProvisionAnotherOperator() throws Exception {
        mockMvc.perform(post("/api/auth/users")
                        .contentType("application/json")
                        .content("{\"username\":\"second\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanProvisionOperatorWithoutReturningPasswordHash() throws Exception {
        when(authService.createOperator("new.operator", "long-enough-password"))
                .thenReturn(new AuthService.UserSummary(9L, "new.operator", UserRole.OPERATOR, Instant.now()));

        mockMvc.perform(post("/api/auth/users")
                        .contentType("application/json")
                        .content("{\"username\":\"new.operator\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new.operator"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
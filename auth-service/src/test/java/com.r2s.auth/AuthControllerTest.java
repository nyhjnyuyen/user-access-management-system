package com.r2s.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r2s.auth.config.RateLimitFilter;
import com.r2s.auth.config.SecurityConfig;
import com.r2s.auth.controller.AuthController;
import com.r2s.auth.dto.AuthResponse;
import com.r2s.auth.dto.LoginRequest;
import com.r2s.auth.dto.RegisterRequest;
import com.r2s.auth.service.AuthService;
import com.r2s.core.exception.CustomException;
import com.r2s.core.exception.GlobalExceptionHandler;
import com.r2s.core.security.JwtFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import({
        AuthControllerTest.TestSecurityConfig.class,
        GlobalExceptionHandler.class
})
class AuthControllerTest {

    @MockBean
    private AuthService authService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, res, e ) -> res.sendError(401))
                            .accessDeniedHandler((req,res,e) -> res.sendError(403))
                    )
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/auth/register", "/auth/login", "/auth/activate/**").permitAll()
                            .requestMatchers(HttpMethod.DELETE, "/auth/**").authenticated()
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }

    @Test
    @DisplayName("register_returns200_whenRequestValid")
    void register_returns200_whenRequestValid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john");
        request.setPassword("12345678");
        request.setEmail("john@example.com");

        doNothing().when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("User registered successfully"));

        verify(authService).register(any(RegisterRequest.class));
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("register_returns400_whenBodyInvalid")
    void register_returns400_whenBodyInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("");
        request.setEmail("bad-email");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("login_returns200_andToken_whenCredentialsValid")
    void login_returns200_andToken_whenCredentialsValid() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("john");
        request.setPassword("12345678");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("jwt-token"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(authService).login(any(LoginRequest.class));
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("login_returns400_whenBodyInvalid")
    void login_returns400_whenBodyInvalid() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(LoginRequest.class));
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("activate_returns200_whenTokenValid")
    void activate_returns200_whenTokenValid() throws Exception {
        doNothing().when(authService).activateAccount("test-token");

        mockMvc.perform(get("/auth/activate/test-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account activated successfully"));

        verify(authService).activateAccount("test-token");
        verifyNoMoreInteractions(authService);
    }

    @Test
    void activate_shouldReturn404_whenTokenInvalid() throws Exception {
        doThrow(new CustomException(HttpStatus.NOT_FOUND, "Invalid token"))
                .when(authService).activateAccount("bad-token");

        mockMvc.perform(get("/auth/activate/bad-token"))
                .andExpect(status().isNotFound());

        verify(authService).activateAccount("bad-token");
    }

    @Test
    @DisplayName("activate_returns409_whenTokenAlreadyUsed")
    void activate_returns409_whenTokenAlreadyUsed() throws Exception {
        doThrow(new CustomException(HttpStatus.CONFLICT, "Token already used"))
                .when(authService).activateAccount("used-token");

        mockMvc.perform(get("/auth/activate/used-token"))
                .andExpect(status().isConflict());

        verify(authService).activateAccount("used-token");
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("activate_returns401_whenTokenExpired")
    void activate_returns401_whenTokenExpired() throws Exception {
        doThrow(new CustomException(HttpStatus.UNAUTHORIZED, "Token expired"))
                .when(authService).activateAccount("expired-token");

        mockMvc.perform(get("/auth/activate/expired-token"))
                .andExpect(status().isUnauthorized());

        verify(authService).activateAccount("expired-token");
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("deleteUser_returns200_whenRequesterIsAdmin")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deleteUser_returns200_whenRequesterIsAdmin() throws Exception {
        doNothing().when(authService).deleteUser("john");

        mockMvc.perform(delete("/auth/john"))
                .andExpect(status().isOk())
                .andExpect(content().string("User deleted successfully"));

        verify(authService).deleteUser("john");
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("deleteUser_returns403_whenRequesterIsNotAdmin")
    @WithMockUser(username = "user", roles = {"USER"})
    void deleteUser_returns403_whenRequesterIsNotAdmin() throws Exception {
        mockMvc.perform(delete("/auth/john"))
                .andExpect(status().isForbidden());

        verify(authService, never()).deleteUser(any());
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("deleteUser_returns401_whenTokenDoesntExist")
    void deleteUser_returns401_whenTokenDoesntExist() throws Exception {
        mockMvc.perform(delete("/auth/jess"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).deleteUser(any());
        verifyNoMoreInteractions(authService);
    }
}
